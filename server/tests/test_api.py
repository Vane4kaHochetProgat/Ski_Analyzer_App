from .conftest import auth_headers, make_user_payload


def test_health_returns_ok(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["db"] is True


def test_register_returns_user_and_token(client, cleanup):
    payload = make_user_payload()
    r = client.post("/users", json=payload)
    assert r.status_code == 201
    body = r.json()
    assert body["token_type"] == "bearer"
    assert len(body["access_token"]) > 50
    assert body["user"]["username"] == payload["username"]
    assert body["user"]["email"] == payload["email"]
    assert body["user"]["is_active"] is True
    assert "password_hash" not in body["user"]
    cleanup.append(body["user"]["user_id"])


def test_register_duplicate_email_returns_409(client, cleanup, registered):
    payload, _, _ = registered
    second = make_user_payload()
    second["email"] = payload["email"]
    r = client.post("/users", json=second)
    assert r.status_code == 409


def test_login_success_returns_token(client, registered):
    payload, _, _ = registered
    r = client.post("/login", json={
        "email": payload["email"], "password": payload["password"]
    })
    assert r.status_code == 200
    body = r.json()
    assert body["token_type"] == "bearer"
    assert body["user"]["email"] == payload["email"]


def test_login_wrong_password_returns_401(client, registered):
    payload, _, _ = registered
    r = client.post("/login", json={
        "email": payload["email"], "password": "wrong"
    })
    assert r.status_code == 401


def test_me_without_token_returns_401(client):
    r = client.get("/me")
    assert r.status_code == 401


def test_me_with_token_returns_user(client, registered):
    payload, token, _ = registered
    r = client.get("/me", headers=auth_headers(token))
    assert r.status_code == 200
    assert r.json()["email"] == payload["email"]


def test_me_with_bogus_token_returns_401(client):
    r = client.get("/me", headers=auth_headers("not.a.real.jwt"))
    assert r.status_code == 401


def test_full_video_analysis_pipeline(client, registered):
    _, token, _ = registered
    h = auth_headers(token)

    no_auth = client.post("/videos", json={
        "title": "x", "sport_code": "skiing", "file_path": "/tmp/x.mp4"
    })
    assert no_auth.status_code == 401

    r = client.post("/videos", json={
        "title": "demo", "sport_code": "skiing", "file_path": "/tmp/demo.mp4"
    }, headers=h)
    assert r.status_code == 201, r.text
    video = r.json()
    assert video["title"] == "demo"

    r = client.post("/analyses", json={
        "video_id": video["video_id"],
        "overall_score": 72.0,
        "status": "completed",
        "angles": [
            {"angle": "left_knee_angle", "percent_bad": 80.0,
             "mean_diff_deg": 8.0, "max_diff_deg": 20.0, "is_critical": True},
        ],
        "recommendations": ["Согните больше колено"],
    }, headers=h)
    assert r.status_code == 201, r.text
    analysis = r.json()

    r = client.post("/user_mistakes", json={
        "analysis_id": analysis["analysis_id"],
        "mistake_code": "leaning_back",
    }, headers=h)
    assert r.status_code == 201, r.text

    r = client.post("/user_mistakes", json={
        "analysis_id": analysis["analysis_id"],
        "mistake_code": "leaning_back",
    }, headers=h)
    assert r.status_code == 409

    r = client.get("/me/videos", headers=h)
    assert r.status_code == 200
    ids = [v["video_id"] for v in r.json()]
    assert video["video_id"] in ids

    r = client.get("/me/mistakes", headers=h)
    assert r.status_code == 200
    codes = [m["mistake_code"] for m in r.json()]
    assert "leaning_back" in codes


def test_create_video_with_bad_sport_code_returns_400(client, registered):
    _, token, _ = registered
    r = client.post("/videos", json={
        "title": "x", "sport_code": "curling", "file_path": "/tmp/x.mp4"
    }, headers=auth_headers(token))
    assert r.status_code == 400


def test_user_cannot_see_other_users_data(client, registered):
    payload_a, token_a, _ = registered

    payload_b = make_user_payload()
    rb = client.post("/users", json=payload_b)
    assert rb.status_code == 201
    token_b = rb.json()["access_token"]
    user_b_id = rb.json()["user"]["user_id"]

    rv = client.post("/videos", json={
        "title": "private_b", "sport_code": "skiing", "file_path": "/tmp/b.mp4"
    }, headers=auth_headers(token_b))
    assert rv.status_code == 201

    ra = client.get("/me/videos", headers=auth_headers(token_a))
    titles = [v["title"] for v in ra.json()]
    assert "private_b" not in titles

    import psycopg
    from app.db import DATABASE_URL
    with psycopg.connect(DATABASE_URL) as conn:
        conn.execute("DELETE FROM users WHERE user_id = %s", (user_b_id,))


def test_mistakes_catalog_returns_seeded_entries(client):
    r = client.get("/mistakes")
    assert r.status_code == 200
    body = r.json()
    assert isinstance(body, list)
    assert len(body) >= 5
    codes = {m["code"] for m in body}
    assert "leaning_back" in codes
