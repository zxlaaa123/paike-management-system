import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const DEFAULT_DEV_HOST = '127.0.0.1'

function resolveDevHost(value: string | undefined): string | boolean {
  const host = value?.trim()

  if (!host) {
    return DEFAULT_DEV_HOST
  }

  if (host.toLowerCase() === 'true') {
    return true
  }

  return host
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
      }),
      Components({
        resolvers: [ElementPlusResolver()],
      }),
    ],
    server: {
      port: 5173,
      host: resolveDevHost(env.DEV_SERVER_HOST),
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8090',
          changeOrigin: true,
        },
      },
    },
  }
})
