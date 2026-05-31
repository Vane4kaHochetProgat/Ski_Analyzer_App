import os
import sys
import uuid
from pathlib import Path

import pytest
import psycopg

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("JWT_SECRET", "test-secret-not-for-prod")

from fastapi.testclient import TestClient  # noqa: E402
from app.main import app  # noqa: E402
from app.db import DATABASE_URL  # noqa: E402


@pytest.fixture(scope="session")
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def cleanup():
    created = []
    yield created
    if not created:
        return
    with psycopg.connect(DATABASE_URL) as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM users WHERE user_id = ANY(%s)", (created,))


def make_user_payload() -> dict:
    suffix = uuid.uuid4().hex[:8]
    return {
        "username": f"u_{suffix}",
        "email": f"{suffix}@example.test",
        "password": "Secret_1234",
    }


@pytest.fixture
def registered(client: TestClient, cleanup):
    payload = make_user_payload()
    resp = client.post("/users", json=payload)
    assert resp.status_code == 201, resp.text
    data = resp.json()
    user_id = data["user"]["user_id"]
    token = data["access_token"]
    cleanup.append(user_id)
    return payload, token, user_id


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}
