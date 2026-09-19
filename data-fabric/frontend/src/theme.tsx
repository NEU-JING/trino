import { ConfigProvider, theme as antdTheme } from "antd";
import zhCN from "antd/locale/zh_CN";
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type ThemeMode = "light" | "dark";

const STORAGE_KEY = "data-fabric-theme";

export const LIGHT_PRIMARY = "#1668dc";
export const DARK_PRIMARY = "#3c89ff";

interface ThemeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  mode: "light",
  setMode: () => undefined,
  toggle: () => undefined,
});

export function useThemeMode(): ThemeContextValue {
  return useContext(ThemeContext);
}

function readStoredMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark") {
      return stored;
    }
  } catch {
    // ignore storage access errors
  }
  return "light";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // ignore storage access errors
    }
  }, [mode]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      mode,
      setMode: setModeState,
      toggle: () => setModeState((current) => (current === "light" ? "dark" : "light")),
    }),
    [mode],
  );

  return (
    <ThemeContext.Provider value={value}>
      <ConfigProvider
        locale={zhCN}
        theme={{
          algorithm: mode === "dark" ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
          token: {
            colorPrimary: mode === "dark" ? DARK_PRIMARY : LIGHT_PRIMARY,
            borderRadius: 8,
            fontFamily: "'Inter', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif",
          },
        }}
      >
        {children}
      </ConfigProvider>
    </ThemeContext.Provider>
  );
}
