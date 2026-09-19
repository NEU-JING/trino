import { Spin } from "antd";

export default function PageLoader() {
  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 240 }}>
      <Spin size="large" />
    </div>
  );
}
