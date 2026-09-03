import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// Geliştirmede API çağrıları yerel sunucuya (Spring Boot :8734) proxy'lenir.
// Üretimde `npm run build` çıktısı server/src/main/resources/static altına kopyalanır.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": path.resolve(__dirname, "src") },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8734", changeOrigin: true },
      "/actuator": { target: "http://localhost:8734", changeOrigin: true },
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
});
