FROM python:3.12-slim
WORKDIR /app
COPY requirements.lock.txt .
RUN pip install --no-cache-dir -r requirements.lock.txt && useradd --uid 10001 --create-home runner && mkdir /data && chown runner /data
COPY main.py workflow.py ./
USER runner
ENV CHECKPOINT_DB=/data/checkpoints.sqlite
EXPOSE 8093
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8093"]
