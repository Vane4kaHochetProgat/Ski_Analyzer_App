import os
from typing import List
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Response
import bcrypt
import psycopg

from .db import get_conn
from .models import (
    UserRegister, UserLogin, UserOut,
    MistakeOut,
    VideoCreate, VideoOut,
    AnalysisCreate, AnalysisOut,
    UserMistakeCreate, UserMistakeOut, UserMistakeDetail,
)

load_dotenv()
app = FastAPI(title="Technique analysis API", version="0.1.0")


@app.get("/health")
def health():
    with get_conn() as conn:
        row = conn.execute("SELECT 1 AS ok").fetchone()
    return {"status": "ok", "db": row["ok"] == 1}


@app.post("/users", response_model=UserOut, status_code=201)
def register(user: UserRegister):
    pw_hash = bcrypt.hashpw(user.password.encode(), bcrypt.gensalt()).decode()
    with get_conn() as conn:
        try:
            row = conn.execute(
                """
                INSERT INTO users (username, email, password_hash)
                VALUES (%s, %s, %s)
                RETURNING user_id, username, email, created_at, is_active
                """,
                (user.username, user.email, pw_hash),
            ).fetchone()
        except psycopg.errors.UniqueViolation:
            raise HTTPException(409, "username or email already taken")
    return row


@app.post("/login", response_model=UserOut)
def login(creds: UserLogin):
    with get_conn() as conn:
        row = conn.execute(
            """
            SELECT user_id, username, email, password_hash, created_at, is_active
              FROM users
             WHERE email = %s
            """,
            (creds.email,),
        ).fetchone()
    if not row or not row["is_active"]:
        raise HTTPException(401, "invalid email or password")
    if not bcrypt.checkpw(creds.password.encode(), row["password_hash"].encode()):
        raise HTTPException(401, "invalid email or password")
    # strip password_hash before returning
    row.pop("password_hash", None)
    return row


@app.get("/mistakes", response_model=List[MistakeOut])
def list_mistakes():
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT mt.mistake_type_id,
                   mt.code,
                   mt.title,
                   mt.description,
                   sev.code AS severity,
                   sp.code  AS sport,
                   mt.icon_code,
                   mt.icon_tint_hex,
                   stats.user_share
              FROM mistake_types mt
              JOIN severities sev ON sev.severity_id = mt.default_severity_id
              JOIN sports     sp  ON sp.sport_id     = mt.sport_id
              LEFT JOIN mistake_user_stats stats
                     ON stats.mistake_type_id = mt.mistake_type_id
             ORDER BY mt.mistake_type_id
            """
        ).fetchall()
    return [
        {**r, "user_share": float(r["user_share"]) if r["user_share"] is not None else None}
        for r in rows
    ]


@app.post("/videos", response_model=VideoOut, status_code=201)
def create_video(v: VideoCreate):
    with get_conn() as conn:
        sport = conn.execute(
            "SELECT sport_id FROM sports WHERE code = %s", (v.sport_code,)
        ).fetchone()
        if not sport:
            raise HTTPException(400, f"unknown sport_code: {v.sport_code}")
        try:
            row = conn.execute(
                """
                INSERT INTO videos (user_id, title, sport_id, file_path, annotated_path)
                VALUES (%s, %s, %s, %s, %s)
                RETURNING video_id, user_id, title, sport_id, file_path, annotated_path, uploaded_at
                """,
                (v.user_id, v.title, sport["sport_id"], v.file_path, v.annotated_path),
            ).fetchone()
        except psycopg.errors.ForeignKeyViolation:
            raise HTTPException(400, "user_id does not exist")
    return row


@app.get("/users/{user_id}/videos", response_model=List[VideoOut])
def list_user_videos(user_id: int):
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT video_id, user_id, title, sport_id, file_path, annotated_path, uploaded_at
              FROM videos
             WHERE user_id = %s
             ORDER BY uploaded_at DESC
            """,
            (user_id,),
        ).fetchall()
    return rows


@app.delete("/videos/{video_id}", status_code=204)
def delete_video(video_id: int, user_id: int):
    with get_conn() as conn:
        row = conn.execute(
            "SELECT user_id FROM videos WHERE video_id = %s",
            (video_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "video not found")
        if row["user_id"] != user_id:
            raise HTTPException(403, "this video belongs to another user")
        conn.execute("DELETE FROM videos WHERE video_id = %s", (video_id,))
    return Response(status_code=204)


@app.post("/analyses", response_model=AnalysisOut, status_code=201)
def create_analysis(a: AnalysisCreate):
    with get_conn() as conn:
        with conn.transaction():
            try:
                analysis = conn.execute(
                    """
                    INSERT INTO analyses (video_id, overall_score, charts_path, status)
                    VALUES (%s, %s, %s, %s)
                    RETURNING analysis_id, video_id, overall_score, charts_path, status, created_at
                    """,
                    (a.video_id, a.overall_score, a.charts_path, a.status),
                ).fetchone()
            except psycopg.errors.UniqueViolation:
                raise HTTPException(409, "analysis already exists for this video")
            except psycopg.errors.ForeignKeyViolation:
                raise HTTPException(400, "video_id does not exist")

            for ang in a.angles:
                conn.execute(
                    """
                    INSERT INTO angle_analyses
                        (analysis_id, angle, percent_bad, mean_diff_deg, max_diff_deg, is_critical)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    (analysis["analysis_id"], ang.angle, ang.percent_bad,
                     ang.mean_diff_deg, ang.max_diff_deg, ang.is_critical),
                )
            for pos, text in enumerate(a.recommendations):
                conn.execute(
                    """
                    INSERT INTO recommendations (analysis_id, text, position)
                    VALUES (%s, %s, %s)
                    """,
                    (analysis["analysis_id"], text, pos),
                )
    return {**analysis, "overall_score": float(analysis["overall_score"]) if analysis["overall_score"] is not None else None}


@app.post("/user_mistakes", response_model=UserMistakeOut, status_code=201)
def record_user_mistake(m: UserMistakeCreate):
    with get_conn() as conn:
        mt = conn.execute(
            "SELECT mistake_type_id, default_severity_id FROM mistake_types WHERE code = %s",
            (m.mistake_code,),
        ).fetchone()
        if not mt:
            raise HTTPException(400, f"unknown mistake_code: {m.mistake_code}")

        if m.severity_code is None:
            severity_id = mt["default_severity_id"]
        else:
            sev = conn.execute(
                "SELECT severity_id FROM severities WHERE code = %s", (m.severity_code,)
            ).fetchone()
            if not sev:
                raise HTTPException(400, f"unknown severity_code: {m.severity_code}")
            severity_id = sev["severity_id"]

        try:
            row = conn.execute(
                """
                INSERT INTO user_mistakes
                    (user_id, analysis_id, mistake_type_id, severity_id, notes)
                VALUES (%s, %s, %s, %s, %s)
                RETURNING user_mistake_id, user_id, analysis_id,
                          mistake_type_id, severity_id, detected_at, notes
                """,
                (m.user_id, m.analysis_id, mt["mistake_type_id"], severity_id, m.notes),
            ).fetchone()
        except psycopg.errors.UniqueViolation:
            raise HTTPException(409, "this mistake is already recorded for this analysis")
        except psycopg.errors.ForeignKeyViolation as e:
            raise HTTPException(400, f"foreign key violation: {e}")
    return row


@app.get("/users/{user_id}/mistakes", response_model=List[UserMistakeDetail])
def user_mistakes(user_id: int):
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT um.user_mistake_id,
                   um.analysis_id,
                   mt.code        AS mistake_code,
                   mt.title,
                   mt.description,
                   sev.code       AS severity,
                   sp.code        AS sport,
                   mt.icon_code,
                   mt.icon_tint_hex,
                   um.detected_at,
                   um.notes
              FROM user_mistakes um
              JOIN mistake_types mt  ON mt.mistake_type_id = um.mistake_type_id
              JOIN severities    sev ON sev.severity_id    = um.severity_id
              JOIN sports        sp  ON sp.sport_id        = mt.sport_id
             WHERE um.user_id = %s
             ORDER BY um.detected_at DESC
            """,
            (user_id,),
        ).fetchall()
    return rows
