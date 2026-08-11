from fastapi import FastAPI

app = FastAPI(
    title="Rural Aid Voice Service",
    version="0.1.0",
)


@app.get("/health", tags=["operations"])
def health() -> dict[str, str]:
    return {
        "status": "healthy",
        "service": "voice-service",
    }

