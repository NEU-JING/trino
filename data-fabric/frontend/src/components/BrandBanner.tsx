import { DeploymentUnitOutlined } from "@ant-design/icons";

export default function BrandBanner() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
      <span className="df-logo" aria-hidden>
        <DeploymentUnitOutlined />
      </span>
      <div style={{ display: "flex", flexDirection: "column" }}>
        <h1 className="df-title">数据编织平台</h1>
        <p className="df-slogan">D A T A &nbsp; F A B R I C &nbsp;·&nbsp; 一 网 统 览 · 跨 源 即 查</p>
      </div>
    </div>
  );
}
