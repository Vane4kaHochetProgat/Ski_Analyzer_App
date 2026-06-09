import random
import uuid

from locust import HttpUser, between, task

SPORTS = ["skiing"]
MISTAKE_CODES = [
    "leaning_back",
    "arms_too_wide",
    "looking_down",
    "stiff_knees",
    "hip_rotation_issues",
]
ANGLE_NAMES = [
    "left_knee_angle",
    "right_knee_angle",
    "hip_rotation_angle",
    "spine_tilt_angle",
    "shoulder_width_angle",
]


def _angle(name: str) -> dict:
    return {
        "angle": name,
        "percent_bad": round(random.uniform(20, 90), 1),
        "mean_diff_deg": round(random.uniform(2, 15), 1),
        "max_diff_deg": round(random.uniform(10, 40), 1),
        "is_critical": random.random() < 0.3,
    }


class TechniqueAnalysisUser(HttpUser):
    wait_time = between(0.5, 2.5)

    def on_start(self):
        suffix = uuid.uuid4().hex[:10]
        payload = {
            "username": f"u_{suffix}",
            "email": f"{suffix}@load.test",
            "password": "Secret_1234",
        }
        with self.client.post(
            "/users", json=payload, name="/users (register)", catch_response=True
        ) as resp:
            if resp.status_code != 201:
                resp.failure(f"register {resp.status_code}: {resp.text[:120]}")
                self.token = None
                self.headers = {}
                return
            data = resp.json()
            self.token = data["access_token"]
            self.user_id = data["user"]["user_id"]
            self.headers = {"Authorization": f"Bearer {self.token}"}

    @task(1)
    def health(self):
        self.client.get("/health")

    @task(5)
    def catalog(self):
        self.client.get("/mistakes")

    @task(4)
    def my_mistakes(self):
        if not self.token:
            return
        self.client.get("/me/mistakes", headers=self.headers)

    @task(4)
    def my_videos(self):
        if not self.token:
            return
        self.client.get("/me/videos", headers=self.headers)

    @task(2)
    def submit_full_pipeline(self):
        if not self.token:
            return

        title = f"load_{uuid.uuid4().hex[:6]}"
        with self.client.post(
            "/videos",
            json={
                "title": title,
                "sport_code": random.choice(SPORTS),
                "file_path": f"/tmp/{title}.mp4",
            },
            headers=self.headers,
            name="/videos",
            catch_response=True,
        ) as resp:
            if resp.status_code != 201:
                resp.failure(f"create_video {resp.status_code}: {resp.text[:120]}")
                return
            video_id = resp.json()["video_id"]

        angle_count = random.randint(2, 4)
        with self.client.post(
            "/analyses",
            json={
                "video_id": video_id,
                "overall_score": round(random.uniform(40, 95), 1),
                "status": "completed",
                "angles": [_angle(n) for n in random.sample(ANGLE_NAMES, angle_count)],
                "recommendations": ["Согните колени", "Смотрите вперёд"],
            },
            headers=self.headers,
            name="/analyses",
            catch_response=True,
        ) as resp:
            if resp.status_code != 201:
                resp.failure(f"create_analysis {resp.status_code}: {resp.text[:120]}")
                return
            analysis_id = resp.json()["analysis_id"]

        with self.client.post(
            "/user_mistakes",
            json={
                "analysis_id": analysis_id,
                "mistake_code": random.choice(MISTAKE_CODES),
            },
            headers=self.headers,
            name="/user_mistakes",
            catch_response=True,
        ) as resp:
            if resp.status_code not in (201, 409):
                resp.failure(f"record_mistake {resp.status_code}: {resp.text[:120]}")


class AnonymousReader(HttpUser):
    wait_time = between(1.0, 4.0)
    weight = 2

    @task(3)
    def catalog(self):
        self.client.get("/mistakes")

    @task(1)
    def health(self):
        self.client.get("/health")
