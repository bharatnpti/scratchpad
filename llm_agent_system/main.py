from fastapi import FastAPI

app = FastAPI(title="LLM Agent System")

@app.get("/")
async def root():
    return {"message": "LLM Agent System is running"}

# Further imports and routes will be added here
