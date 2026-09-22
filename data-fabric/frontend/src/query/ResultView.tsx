import { CopyOutlined } from "@ant-design/icons";
import {
  Alert,
  App as AntdApp,
  Button,
  Empty,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import ReactECharts from "echarts-for-react";
import { useMemo, useState } from "react";
import type { QueryExecutionView } from "../api";

interface Props {
  view: QueryExecutionView | null;
  dark: boolean;
}

interface CellProps {
  value: unknown;
}

function copyText(text: string): void {
  if (navigator.clipboard?.writeText) {
    void navigator.clipboard.writeText(text).catch(() => undefined);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  try {
    document.execCommand("copy");
  } finally {
    textarea.remove();
  }
}

function CellValue({ value }: CellProps) {
  const { message } = AntdApp.useApp();
  if (value === null || value === undefined) {
    return (
      <Typography.Text type="secondary" italic>
        NULL
      </Typography.Text>
    );
  }
  const text = typeof value === "string" ? value : String(value);
  if (text === "") {
    return (
      <Typography.Text type="secondary" italic>
        （空字符串）
      </Typography.Text>
    );
  }
  const display = text.length > 80 ? text.slice(0, 80) + "…" : text;
  return (
    <span className="df-cell">
      <Tooltip
        title={text.length > 80 ? <pre className="df-cell-full">{text}</pre> : text}
        placement="topLeft"
        mouseEnterDelay={0.3}
      >
        <span className="df-cell-text">{display}</span>
      </Tooltip>
      <Button
        className="df-copy"
        type="text"
        size="small"
        icon={<CopyOutlined />}
        onClick={(event) => {
          event.stopPropagation();
          copyText(text);
          message.success("已复制");
        }}
      />
    </span>
  );
}

function compareValues(left: unknown, right: unknown): number {
  if (left === null || left === undefined) {
    return right === null || right === undefined ? 0 : 1;
  }
  if (right === null || right === undefined) {
    return -1;
  }
  if (typeof left === "number" && typeof right === "number") {
    return left - right;
  }
  const leftNumber = Number(left);
  const rightNumber = Number(right);
  if (!Number.isNaN(leftNumber) && !Number.isNaN(rightNumber) && left !== "" && right !== "") {
    return leftNumber - rightNumber;
  }
  return String(left).localeCompare(String(right));
}

function isNumericColumn(rows: unknown[][], index: number): boolean {
  return rows.some((row) => {
    const value = row[index];
    if (typeof value === "number") {
      return true;
    }
    return typeof value === "string" && value.trim() !== "" && !Number.isNaN(Number(value));
  });
}

function ResizableTitle(props: {
  onResize?: (event: React.MouseEvent) => void;
  width?: number;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}) {
  const { onResize, width, children, style, ...restProps } = props;
  if (width === undefined || !onResize) {
    return (
      <th style={style} {...(restProps as React.ThHTMLAttributes<HTMLTableCellElement>)}>
        {children}
      </th>
    );
  }
  return (
    <th style={{ ...style, position: "relative" }} {...(restProps as React.ThHTMLAttributes<HTMLTableCellElement>)}>
      {children}
      <span
        onMouseDown={onResize}
        style={{
          position: "absolute",
          right: 0,
          top: 0,
          bottom: 0,
          width: 8,
          cursor: "col-resize",
          userSelect: "none",
          touchAction: "none",
        }}
      />
    </th>
  );
}

export default function ResultView({ view, dark }: Props) {
  const [widths, setWidths] = useState<Record<string, number>>({});
  const [mode, setMode] = useState<"table" | "chart">("table");
  const [chartType, setChartType] = useState<"bar" | "line" | "pie">("bar");
  const [valueIndex, setValueIndex] = useState<number>(0);
  const [categoryIndex, setCategoryIndex] = useState<number>(0);

  const columns = view?.columns ?? [];
  const rows = view?.rows ?? [];

  function startResize(key: string, initialWidth: number) {
    return (event: React.MouseEvent) => {
      event.preventDefault();
      event.stopPropagation();
      const startX = event.clientX;
      const onMove = (moveEvent: MouseEvent) => {
        const next = Math.max(80, initialWidth + moveEvent.clientX - startX);
        setWidths((previous) => ({ ...previous, [key]: next }));
      };
      const onUp = () => {
        document.removeEventListener("mousemove", onMove);
        document.removeEventListener("mouseup", onUp);
      };
      document.addEventListener("mousemove", onMove);
      document.addEventListener("mouseup", onUp);
    };
  }

  const numericColumns = useMemo(
    () => columns.map((_, index) => index).filter((index) => isNumericColumn(rows, index)),
    [columns, rows],
  );
  const effectiveValueIndex = numericColumns.includes(valueIndex) ? valueIndex : (numericColumns[0] ?? -1);
  const effectiveCategoryIndex =
    categoryIndex !== effectiveValueIndex
      ? categoryIndex
      : [0, 1, 2].find((index) => index < columns.length && index !== effectiveValueIndex) ?? 0;

  const widthOf = (key: string) => widths[key] ?? 160;

  const tableColumns: ColumnsType<Record<string, unknown>> = columns.map((column, index) => {
    const key = "c" + index;
    return {
      title: column,
      dataIndex: key,
      key,
      width: widthOf(key),
      sorter: (left, right) => compareValues(left[key], right[key]),
      ellipsis: { showTitle: false },
      render: (value: unknown) => <CellValue value={value} />,
      onHeaderCell: () =>
        ({
          width: widthOf(key),
          onResize: startResize(key, widthOf(key)),
        }) as unknown as React.ThHTMLAttributes<HTMLTableCellElement>,
    };
  });

  const dataSource = rows.map((row, rowIndex) => {
    const record: Record<string, unknown> = { key: rowIndex };
    row.forEach((value, columnIndex) => {
      record["c" + columnIndex] = value;
    });
    return record;
  });

  const totalWidth = columns.reduce((sum, _, index) => sum + widthOf("c" + index), 0);

  const chartOption = useMemo(() => {
    if (effectiveValueIndex < 0) {
      return null;
    }
    const labelColor = dark ? "#e6f0ff" : "#0b1f3a";
    const limit = chartType === "pie" ? 30 : 500;
    const sliced = dataSource.slice(0, limit);
    const names = sliced.map((record, rowIndex) =>
      effectiveCategoryIndex >= 0 && effectiveCategoryIndex !== effectiveValueIndex
        ? String(record["c" + effectiveCategoryIndex] ?? "")
        : String(rowIndex + 1),
    );
    const values = sliced.map((record) => {
      const numeric = Number(record["c" + effectiveValueIndex]);
      return Number.isNaN(numeric) ? 0 : numeric;
    });
    if (chartType === "pie") {
      return {
        tooltip: { trigger: "item" },
        legend: { type: "scroll", textStyle: { color: labelColor } },
        series: [
          {
            type: "pie",
            radius: ["30%", "70%"],
            data: names.map((name, index) => ({ name, value: values[index] })),
            label: { color: labelColor },
          },
        ],
      };
    }
    return {
      tooltip: { trigger: "axis" },
      grid: { left: 48, right: 24, top: 24, bottom: 48 },
      xAxis: { type: "category", data: names, axisLabel: { color: labelColor } },
      yAxis: { type: "value", axisLabel: { color: labelColor } },
      series: [{ type: chartType, data: values, smooth: true }],
    };
  }, [chartType, dataSource, dark, effectiveCategoryIndex, effectiveValueIndex]);

  if (!view) {
    return <Empty description="执行查询后在此查看结果" style={{ marginTop: 48 }} />;
  }

  const durationMillis =
    view.finishedAt !== null ? new Date(view.finishedAt).getTime() - new Date(view.startedAt).getTime() : null;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0, gap: 8 }}>
      <Space wrap>
        <Tag color="blue">状态：{view.state}</Tag>
        <Tag>返回 {rows.length} 行</Tag>
        <Tag>耗时 {durationMillis === null ? "-" : `${durationMillis} ms`}</Tag>
        <Radio.Group size="small" value={mode} onChange={(event) => setMode(event.target.value)}>
          <Radio.Button value="table">表格</Radio.Button>
          <Radio.Button value="chart" disabled={numericColumns.length === 0}>
            图表
          </Radio.Button>
        </Radio.Group>
        {mode === "chart" && (
          <>
            <Select
              size="small"
              value={chartType}
              style={{ width: 90 }}
              onChange={setChartType}
              options={[
                { value: "bar", label: "柱状" },
                { value: "line", label: "折线" },
                { value: "pie", label: "饼图" },
              ]}
            />
            <Select
              size="small"
              value={effectiveValueIndex}
              style={{ width: 140 }}
              onChange={setValueIndex}
              options={numericColumns.map((index) => ({ value: index, label: "数值：" + columns[index] }))}
            />
            <Select
              size="small"
              value={effectiveCategoryIndex}
              style={{ width: 140 }}
              onChange={setCategoryIndex}
              options={columns.map((column, index) => ({ value: index, label: "分类：" + column }))}
            />
          </>
        )}
      </Space>

      {view.error && <Alert type="error" showIcon message={view.error} />}
      {view.truncated && <Alert type="warning" showIcon message="结果已截断，仅显示前部分行。" />}

      {mode === "table" ? (
        <div style={{ flex: 1, minHeight: 0 }}>
          <Table<Record<string, unknown>>
            size="small"
            rowKey="key"
            tableLayout="fixed"
            components={{ header: { cell: ResizableTitle } }}
            columns={tableColumns}
            dataSource={dataSource}
            scroll={{ x: Math.max(totalWidth, 320), y: 320 }}
            pagination={{
              pageSize: 50,
              showSizeChanger: true,
              pageSizeOptions: [20, 50, 100, 200],
              size: "small",
              showTotal: (total) => `共 ${total} 行`,
            }}
            locale={{ emptyText: <Empty description="（无数据）" /> }}
          />
        </div>
      ) : (
        <div style={{ flex: 1, minHeight: 0 }}>
          {chartOption ? (
            <ReactECharts option={chartOption} style={{ height: "100%", minHeight: 280 }} notMerge />
          ) : (
            <Empty description="结果中没有可用于绘图的数值列" />
          )}
        </div>
      )}
    </div>
  );
}
