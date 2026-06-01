#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

# 环境变量
if [ -f "$ROOT/.env" ]; then
  export $(grep -v '^#' "$ROOT/.env" | xargs)
fi

# 激活 conda 并启动后端（后台）
CONDA_BASE=$(conda info --base 2>/dev/null)
source "$CONDA_BASE/etc/profile.d/conda.sh"
conda activate myagent

cd "$ROOT/backend"
python -m app.main &
BACKEND_PID=$!

# 启动前端（后台）
cd "$ROOT/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "=========================================="
echo "  MyAgent 已启动"
echo "  后端 PID: $BACKEND_PID (port ${APP_PORT:-8765})"
echo "  前端 PID: $FRONTEND_PID (port 5173)"
echo "  访问: http://127.0.0.1:5173"
echo "  Ctrl+C 停止所有服务"
echo "=========================================="
echo ""

# 捕获 Ctrl+C，清理子进程
cleanup() {
  echo ""
  echo "正在停止服务..."
  kill $BACKEND_PID 2>/dev/null
  kill $FRONTEND_PID 2>/dev/null
  wait $BACKEND_PID 2>/dev/null
  wait $FRONTEND_PID 2>/dev/null
  echo "Done."
  exit 0
}
trap cleanup SIGINT SIGTERM

# 等待任一进程退出
wait
