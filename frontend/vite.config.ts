import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // In development the browser calls Vite, which forwards API calls to Spring Boot.
    proxy: { '/api': 'http://localhost:8080' },
  },
});
