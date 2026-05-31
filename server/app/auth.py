import os
import sys
from datetime import datetime, timedelta, timezone

from fastapi import Depends, HTTPException, Request
from jose import JWTError, jwt

from .db import get_conn

JWT_ALG = "HS256"
JWT_TTL_DAYS = 30
JWT_SECRET = os.getenv("JWT_SECRET")
if not JWT_SECRET:
    print(
        "WARNING: JWT_SECRET not set — using insecure dev default. "
        "Set JWT_SECRET in .env for any real deployment.",
        file=sys.stderr,
    )
    JWT_SECRET = "insecure-dev-secret-do-not-use-in-prod-9f3a8b2c1d"


def create_access_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=JWT_TTL_DAYS)).timestamp()),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALG)


def _decode(token: str) -> int:
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALG])
        sub = payload.get("sub")
        if sub is None:
            raise HTTPException(401, "invalid token: no sub")
        return int(sub)
    except JWTError as e:
        raise HTTPException(401, f"invalid token: {e}")
    except (ValueError, TypeError):
        raise HTTPException(401, "invalid token: malformed sub")


def get_current_user(request: Request) -> dict:
    auth = request.headers.get("authorization") or request.headers.get("Authorization")
    if not auth or not auth.lower().startswith("bearer "):
        raise HTTPException(401, "missing bearer token")
    token = auth.split(" ", 1)[1].strip()
    user_id = _decode(token)
    with get_conn() as conn:
        row = conn.execute(
            "SELECT user_id, username, email, created_at, is_active FROM users WHERE user_id = %s",
            (user_id,),
        ).fetchone()
    if not row or not row["is_active"]:
        raise HTTPException(401, "user not found or deactivated")
    return row


CurrentUser = Depends(get_current_user)
