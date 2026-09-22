import { DeleteOutlined, PlayCircleOutlined, SelectOutlined, StarOutlined } from "@ant-design/icons";
import { App as AntdApp, Button, Empty, Input, List, Popconfirm, Space, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import * as api from "../api";
import type { SavedQueryView } from "../api";

interface Props {
  currentSql: string;
  onExecute: (sql: string) => void;
  onLoad: (sql: string) => void;
}

export default function SavedQueryPanel({ currentSql, onExecute, onLoad }: Props) {
  const { message } = AntdApp.useApp();
  const [name, setName] = useState("");
  const [queries, setQueries] = useState<SavedQueryView[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setQueries(await api.listSavedQueries());
    } catch (e) {
      message.error("加载收藏失败：" + String(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSave() {
    if (!name.trim()) {
      message.warning("请先命名收藏查询");
      return;
    }
    if (!currentSql.trim()) {
      message.warning("当前查询为空");
      return;
    }
    try {
      await api.saveQuery(name.trim(), currentSql);
      message.success("已保存收藏");
      setName("");
      await load();
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onDelete(id: number) {
    try {
      await api.deleteSavedQuery(id);
      message.success("已删除收藏");
      await load();
    } catch (e) {
      message.error(String(e));
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0, gap: 8 }}>
      <Space.Compact style={{ width: "100%" }}>
        <Input
          placeholder="收藏名称，例如：每日订单量"
          value={name}
          onChange={(event) => setName(event.target.value)}
          onPressEnter={() => void onSave()}
        />
        <Button type="primary" icon={<StarOutlined />} onClick={() => void onSave()}>
          保存当前查询
        </Button>
      </Space.Compact>

      <div style={{ flex: 1, overflow: "auto", minHeight: 0 }}>
        <List
          size="small"
          loading={loading}
          dataSource={queries}
          locale={{ emptyText: <Empty description="暂无收藏查询" /> }}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button key="run" type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => onExecute(item.sql)}>
                  执行
                </Button>,
                <Button key="load" type="link" size="small" icon={<SelectOutlined />} onClick={() => onLoad(item.sql)}>
                  载入
                </Button>,
                <Popconfirm
                  key="delete"
                  title="确认删除该收藏？"
                  onConfirm={() => void onDelete(item.id)}
                  okText="删除"
                  cancelText="取消"
                >
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                title={item.name}
                description={
                  <Typography.Text code ellipsis style={{ maxWidth: 520 }}>
                    {item.sql}
                  </Typography.Text>
                }
              />
            </List.Item>
          )}
        />
      </div>
    </div>
  );
}
