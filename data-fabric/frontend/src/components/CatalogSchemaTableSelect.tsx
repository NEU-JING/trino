import EntitySelect from "./EntitySelect";

export interface TableTarget {
  catalog?: string;
  schema?: string;
  table?: string;
}

interface Props {
  value?: TableTarget;
  onChange?: (value: TableTarget) => void;
  disabled?: boolean;
}

/**
 * Cascading catalog → schema → table selector. Changing a level clears the levels below it and
 * only offers candidates the server has already filtered by visibility.
 */
export default function CatalogSchemaTableSelect({ value, onChange, disabled }: Props) {
  const target = value ?? {};
  const parent = target.catalog && target.schema ? `${target.catalog}.${target.schema}` : undefined;

  return (
    <div style={{ display: "flex", gap: 8, width: "100%" }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <EntitySelect
          type="catalog"
          value={target.catalog}
          disabled={disabled}
          placeholder="catalog"
          allowClear
          onChange={(catalog) => onChange?.({ catalog, schema: undefined, table: undefined })}
        />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <EntitySelect
          type="schema"
          parent={target.catalog}
          value={target.schema}
          disabled={disabled || !target.catalog}
          placeholder="schema"
          allowClear
          onChange={(schema) => onChange?.({ ...target, schema, table: undefined })}
        />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <EntitySelect
          type="table"
          parent={parent}
          value={target.table}
          disabled={disabled || !target.catalog || !target.schema}
          placeholder="table"
          allowClear
          onChange={(table) => onChange?.({ ...target, table })}
        />
      </div>
    </div>
  );
}
