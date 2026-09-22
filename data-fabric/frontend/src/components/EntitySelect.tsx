import { Select, Spin } from "antd";
import { useEffect, useState, type CSSProperties } from "react";
import * as api from "../api";
import type { SuggestionType } from "../api";

interface Props {
  type: SuggestionType;
  parent?: string;
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  style?: CSSProperties;
}

/**
 * Searchable select backed by the platform metadata suggestion endpoint. Options are loaded
 * lazily from the server, so a user can never pick an object they are not allowed to see.
 */
export default function EntitySelect({ type, parent, value, onChange, placeholder, disabled, allowClear, style }: Props) {
  const [options, setOptions] = useState<{ value: string; label: string }[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");

  useEffect(() => {
    if (type !== "catalog" && !parent) {
      setOptions([]);
      return;
    }
    let active = true;
    setLoading(true);
    api
      .suggestMetadata(type, parent, search)
      .then((result) => {
        if (active) {
          setOptions(result.map((item) => ({ value: item.value, label: item.label })));
        }
      })
      .catch(() => {
        if (active) {
          setOptions([]);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [type, parent, search]);

  return (
    <Select
      showSearch
      allowClear={allowClear}
      value={value}
      disabled={disabled}
      placeholder={placeholder ?? type}
      style={{ width: "100%", ...style }}
      options={options}
      loading={loading}
      filterOption={false}
      onSearch={setSearch}
      onChange={(next) => onChange?.(next as string | undefined)}
      notFoundContent={loading ? <Spin size="small" /> : "无可用候选项"}
    />
  );
}
