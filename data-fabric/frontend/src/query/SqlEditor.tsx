import {
  autocompletion,
  type Completion,
  type CompletionContext,
  type CompletionResult,
} from "@codemirror/autocomplete";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { sql } from "@codemirror/lang-sql";
import { bracketMatching, defaultHighlightStyle, indentOnInput, syntaxHighlighting } from "@codemirror/language";
import { Compartment, EditorState, type Extension } from "@codemirror/state";
import { oneDark } from "@codemirror/theme-one-dark";
import { EditorView, keymap, lineNumbers, placeholder } from "@codemirror/view";
import { forwardRef, useEffect, useImperativeHandle, useRef } from "react";
import * as api from "../api";
import { TRINO_BUILTINS, TRINO_KEYWORDS, TRINO_TYPES, trinoDialect } from "./trinoLanguage";

export interface SqlEditorHandle {
  insertText: (text: string) => void;
  focus: () => void;
  clearCompletionCache: () => void;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  onRun: () => void;
  mode: "light" | "dark";
}

const KEYWORD_OPTIONS: Completion[] = [
  ...TRINO_KEYWORDS.map((keyword) => ({ label: keyword, type: "keyword" })),
  ...TRINO_TYPES.map((type) => ({ label: type, type: "type" })),
  ...TRINO_BUILTINS.map((builtin) => ({ label: builtin, type: "function" })),
];

const SUGGESTION_CACHE = new Map<string, api.SuggestionView[]>();

async function loadSuggestions(type: api.SuggestionType, parent?: string): Promise<api.SuggestionView[]> {
  const key = type + "|" + (parent ?? "");
  const cached = SUGGESTION_CACHE.get(key);
  if (cached) {
    return cached;
  }
  try {
    const result = await api.suggestMetadata(type, parent);
    SUGGESTION_CACHE.set(key, result);
    return result;
  } catch {
    return [];
  }
}

function suggestionType(parentSegments: string[]): api.SuggestionType | null {
  if (parentSegments.length === 0) {
    return null;
  }
  if (parentSegments.length === 1) {
    return "schema";
  }
  if (parentSegments.length === 2) {
    return "table";
  }
  if (parentSegments.length === 3) {
    return "column";
  }
  return null;
}

async function trinoCompletion(context: CompletionContext): Promise<CompletionResult | null> {
  const word = context.matchBefore(/[\w."$]*$/);
  if (!word) {
    return null;
  }
  if (word.from === word.to && !context.explicit) {
    return null;
  }

  const text = word.text;
  const segments = text.split(".");
  const dotted = segments.length > 1 || text.endsWith(".");
  const parentSegments = dotted ? segments.slice(0, -1).filter((segment) => segment.length > 0) : [];
  const partialLength = text.endsWith(".") ? 0 : (segments[segments.length - 1] ?? "").length;
  const from = word.from + (text.length - partialLength);

  const type = suggestionType(parentSegments);
  const options: Completion[] = [];
  if (type === null) {
    options.push(...KEYWORD_OPTIONS);
    const catalogs = await loadSuggestions("catalog");
    options.push(...catalogs.map((catalog) => ({ label: catalog.label, type: "namespace" })));
  } else {
    const suggestions = await loadSuggestions(type, parentSegments.join("."));
    const completionType = type === "column" ? "property" : "class";
    options.push(...suggestions.map((suggestion) => ({
      label: suggestion.value,
      detail: suggestion.label === suggestion.value ? undefined : suggestion.label,
      type: completionType,
    })));
  }

  return { from, options, validFor: /^[\w."$]*$/ };
}

function themeExtension(mode: "light" | "dark"): Extension {
  return mode === "dark" ? oneDark : [];
}

export const SqlEditor = forwardRef<SqlEditorHandle, Props>(function SqlEditor(
  { value, onChange, onRun, mode },
  ref,
) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewRef = useRef<EditorView | null>(null);
  const themeCompartment = useRef(new Compartment());
  const onChangeRef = useRef(onChange);
  const onRunRef = useRef(onRun);

  onChangeRef.current = onChange;
  onRunRef.current = onRun;

  useImperativeHandle(ref, () => ({
    insertText: (text: string) => {
      const view = viewRef.current;
      if (!view) {
        return;
      }
      view.dispatch(view.state.replaceSelection(text));
      view.focus();
    },
    focus: () => viewRef.current?.focus(),
    clearCompletionCache: () => SUGGESTION_CACHE.clear(),
  }));

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const state = EditorState.create({
      doc: value,
      extensions: [
        lineNumbers(),
        history(),
        indentOnInput(),
        bracketMatching(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        keymap.of([
          { key: "Mod-Enter", run: () => {
            onRunRef.current();
            return true;
          } },
          ...defaultKeymap,
          ...historyKeymap,
          indentWithTab,
        ]),
        sql({ dialect: trinoDialect, upperCaseKeywords: true }),
        autocompletion({ override: [trinoCompletion] }),
        placeholder("输入 SQL，例如 SELECT * FROM oceanbase.ob_source.orders"),
        themeCompartment.current.of(themeExtension(mode)),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            onChangeRef.current(update.state.doc.toString());
          }
        }),
        EditorView.theme({
          "&": { height: "100%", fontSize: "14px" },
          ".cm-scroller": {
            fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
            overflow: "auto",
          },
          ".cm-content": { minHeight: "264px", padding: "8px 0" },
          ".cm-gutters": { minHeight: "264px" },
        }),
      ],
    });
    const view = new EditorView({ state, parent: containerRef.current });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) {
      return;
    }
    const current = view.state.doc.toString();
    if (current !== value) {
      view.dispatch({ changes: { from: 0, to: current.length, insert: value } });
    }
  }, [value]);

  useEffect(() => {
    viewRef.current?.dispatch({
      effects: themeCompartment.current.reconfigure(themeExtension(mode)),
    });
  }, [mode]);

  return <div ref={containerRef} style={{ height: "100%", overflow: "hidden" }} />;
});
