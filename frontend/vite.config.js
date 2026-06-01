import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server on port 3000 (matches the backend CORS whitelist).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
  },
});
