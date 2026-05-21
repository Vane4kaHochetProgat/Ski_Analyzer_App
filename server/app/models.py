from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field

class UserRegister(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    email: str = Field(min_length=3, max_length=255)
    password: str = Field(min_length=8, max_length=128)


class UserLogin(BaseModel):
    email: str = Field(min_length=3, max_length=255)
    password: str = Field(min_length=1, max_length=128)


class UserOut(BaseModel):
    user_id: int
    username: str
    email: str
    created_at: datetime
    is_active: bool


class MistakeOut(BaseModel):
    mistake_type_id: int
    code: str
    title: str
    description: str
    severity: str
    sport: str
    icon_code: Optional[str]
    icon_tint_hex: Optional[str]
    user_share: Optional[float]


class VideoCreate(BaseModel):
    user_id: int
    title: str
    sport_code: str
    file_path: str
    annotated_path: Optional[str] = None


class VideoOut(BaseModel):
    video_id: int
    user_id: int
    title: str
    sport_id: int
    file_path: str
    annotated_path: Optional[str]
    uploaded_at: datetime


class AngleAnalysisIn(BaseModel):
    angle: str
    percent_bad: float
    mean_diff_deg: float
    max_diff_deg: float
    is_critical: bool


class AnalysisCreate(BaseModel):
    video_id: int
    overall_score: Optional[float] = None
    charts_path: Optional[str] = None
    status: str = "completed"
    angles: List[AngleAnalysisIn] = []
    recommendations: List[str] = []


class AnalysisOut(BaseModel):
    analysis_id: int
    video_id: int
    overall_score: Optional[float]
    charts_path: Optional[str]
    status: str
    created_at: datetime

class UserMistakeCreate(BaseModel):
    user_id: int
    analysis_id: int
    mistake_code: str
    severity_code: Optional[str] = None
    notes: Optional[str] = None


class UserMistakeOut(BaseModel):
    user_mistake_id: int
    user_id: int
    analysis_id: int
    mistake_type_id: int
    severity_id: int
    detected_at: datetime
    notes: Optional[str]


class UserMistakeDetail(BaseModel):
    """user_mistake row joined with mistake_types/severities/sports for the
    MistakesScreen — carries everything the UI needs to render a card."""
    user_mistake_id: int
    analysis_id: int
    mistake_code: str
    title: str
    description: str
    severity: str
    sport: str
    icon_code: Optional[str]
    icon_tint_hex: Optional[str]
    detected_at: datetime
    notes: Optional[str]
