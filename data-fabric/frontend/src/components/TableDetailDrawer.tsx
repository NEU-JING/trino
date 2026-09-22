import { Descriptions, Drawer, Empty, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import * as api from "../api";
import type { ColumnMetadataView, SampleRowsView, TableDetailView } from "../api";

interface Props {
  tableId: number | null;
  open: boolean;
  onClose: () => void;
}

export default function TableDetailDrawer({ tableId, open, onClose }: Props) {
  const [detail, setDetail] = useState<TableDetailView | null>(null);
  const [columns, setColumns] = useState<ColumnMetadataView[]>([]);
  const [sample, setSample] = useState<SampleRowsView | null>(null);
  const [loading, setLoading] = useState(false);
  const [sampleLoading, setSampleLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open || tableId === null) {
      return;
    }
    let active = true;
    setLoading(true);
    setError("");
    setDetail(null);
    setColumns([]);
    setSample(null);

    Promise.all([api.getTableDetail(tableId), api.getTableColumns(tableId)])
      .then(([loadedDetail, loadedColumns]) => {
        if (active) {
          setDetail(loadedDetail);
          setColumns(loadedColumns);
        }
      })
      .catch((e) => {
        if (active) {
          setError(String(e));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    setSampleLoading(true);
    api
      .getTableSample(tableId)
      .then((loadedSample) => {
        if (active) {
          setSample(loadedSample);
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) {
          setSampleLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [open, tableId]);

  const columnColumns: ColumnsType<ColumnMetadataView> = [
    { title: "列名", dataIndex: "name" },
    { title: "类型", dataIndex: "type" },
    { title: "可空", dataIndex: "nullable", width: 80, render: (nullable: boolean) => (nullable ? "是" : "否") },
    { title: "注释", dataIndex: "comment", render: (comment: string) => comment || "-" },
  ];

  const sampleColumns: ColumnsType<Record<string, unknown>> = (sample?.columns ?? []).map((column, index) => ({
    title: column,
    dataIndex: "c" + index,
    render: (value: unknown) => (value === null || value === undefined ? "" : String(value)),
  }));

  const sampleRows = (sample?.rows ?? []).map((row, rowIndex) => {
    const record: Record<string, unknown> = { key: rowIndex };
    row.forEach((value, columnIndex) => {
      record["c" + columnIndex] = value;
    });
    return record;
  });

  return (
    <Drawer
      title={detail ? `${detail.catalog}.${detail.schema}.${detail.table}` : "表详情"}
      width={720}
      open={open}
      onClose={onClose}
    >
      {loading ? (
        <Spin />
      ) : error ? (
        <Typography.Text type="danger">加载表详情失败：{error}</Typography.Text>
      ) : (
        detail && (
          <>
            <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="描述">{detail.description || "-"}</Descriptions.Item>
              <Descriptions.Item label="注册人">{detail.registeredBy}</Descriptions.Item>
              <Descriptions.Item label="行数">{detail.rowCount ?? "（未知）"}</Descriptions.Item>
              <Descriptions.Item label="被授权主体">
                {detail.principals.length ? detail.principals.map((principal) => <Tag key={principal}>{principal}</Tag>) : "-"}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5}>列结构</Typography.Title>
            <Table<ColumnMetadataView>
              size="small"
              rowKey="name"
              pagination={false}
              columns={columnColumns}
              dataSource={columns}
              locale={{ emptyText: <Empty description="（无列信息）" /> }}
            />
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              样例行
            </Typography.Title>
            {sampleLoading ? (
              <Spin />
            ) : (
              <Table<Record<string, unknown>>
                size="small"
                rowKey="key"
                columns={sampleColumns}
                dataSource={sampleRows}
                scroll={{ x: "max-content" }}
                pagination={false}
                locale={{ emptyText: <Empty description="（无样例数据）" /> }}
              />
            )}
          </>
        )
      )}
    </Drawer>
  );
}
