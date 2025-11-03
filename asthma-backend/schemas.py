# asthma-backend/schemas.py

from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import datetime
from models import UserRole # Import the enum

# --- User & Auth Schemas ---

class UserBase(BaseModel):
    email: EmailStr
    name: str
    role: UserRole

class UserCreate(UserBase):
    password: str

class User(UserBase):
    id: int
    
    class Config:
        from_attributes = True  # <-- UPDATED

class UserLogin(BaseModel):
    email: EmailStr
    password: str

class Token(BaseModel):
    access_token: str
    token_type: str
    user_role: UserRole # Send role back on login

class TokenData(BaseModel):
    email: Optional[str] = None

# --- PEFR Schemas ---

class BaselinePEFRBase(BaseModel):
    baseline_value: int

class BaselinePEFRCreate(BaselinePEFRBase):
    pass

class BaselinePEFR(BaselinePEFRBase):
    id: int
    owner_id: int

    class Config:
        from_attributes = True  # <-- UPDATED

class PEFRRecordCreate(BaseModel):
    pefr_value: int

class PEFRRecord(BaseModel):
    id: int
    pefr_value: int
    zone: str
    recorded_at: datetime
    owner_id: int

    class Config:
        from_attributes = True  # <-- UPDATED

class PEFRRecordResponse(BaseModel):
    zone: str
    guidance: str
    record: PEFRRecord

# --- Symptom Schemas ---

class SymptomCreate(BaseModel):
    wheeze_rating: Optional[int] = None
    cough_rating: Optional[int] = None
    dust_exposure: Optional[bool] = False
    smoke_exposure: Optional[bool] = False

class Symptom(SymptomCreate):
    id: int
    recorded_at: datetime
    owner_id: int

    class Config:
        from_attributes = True  # <-- UPDATED