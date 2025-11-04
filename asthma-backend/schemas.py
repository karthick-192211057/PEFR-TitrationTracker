# asthma-backend/schemas.py

from pydantic import BaseModel, EmailStr
from typing import Optional, List # Import List
from datetime import datetime
from models import UserRole # Import the enum

# --- Config for all schemas ---
class ConfigBase:
    from_attributes = True

# --- Medication Schemas ---

class MedicationBase(BaseModel):
    name: str
    dose: Optional[str] = None
    schedule: Optional[str] = None

class MedicationCreate(MedicationBase):
    pass

class Medication(MedicationBase):
    id: int
    owner_id: int

    class Config(ConfigBase):
        pass

# --- EmergencyContact Schemas ---

class EmergencyContactBase(BaseModel):
    name: str
    phone_number: str
    
    # --- THIS IS THE FIX ---
    contact_relationship: Optional[str] = None

class EmergencyContactCreate(EmergencyContactBase):
    pass

class EmergencyContact(EmergencyContactBase):
    id: int
    owner_id: int

    class Config(ConfigBase):
        pass

# --- Reminder Schemas ---

class ReminderBase(BaseModel):
    reminder_type: str # "PEFR" or "Medication"
    time: str # "HH:MM"
    frequency: str # "Daily"

class ReminderCreate(ReminderBase):
    pass

class Reminder(ReminderBase):
    id: int
    compliance_count: int
    missed_count: int
    owner_id: int

    class Config(ConfigBase):
        pass

# --- User & Auth Schemas ---

class UserBase(BaseModel):
    email: EmailStr
    name: str
    role: UserRole

class UserCreate(UserBase):
    password: str
    # --- ADDED FIELDS ---
    age: Optional[int] = None
    height: Optional[int] = None
    gender: Optional[str] = None
    contact_number: Optional[str] = None
    address: Optional[str] = None

class User(UserBase):
    id: int
    # --- ADDED FIELDS ---
    age: Optional[int] = None
    height: Optional[int] = None
    gender: Optional[str] = None
    contact_number: Optional[str] = None
    address: Optional[str] = None
    
    # --- ADDED RELATIONSHIPS ---
    medications: List[Medication] = []
    emergency_contacts: List[EmergencyContact] = []
    reminders: List[Reminder] = []
    
    # --- THIS IS THE FIX ---
    baseline: Optional["BaselinePEFR"] = None

    class Config(ConfigBase):
        pass

class UserLogin(BaseModel):
    email: EmailStr
    password: str

class Token(BaseModel):
    access_token: str
    token_type: str
    user_role: UserRole

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

    class Config(ConfigBase):
        pass

class PEFRRecordCreate(BaseModel):
    pefr_value: int
    source: Optional[str] = "manual" # Allow setting source on creation

class PEFRRecord(BaseModel):
    id: int
    pefr_value: int
    zone: str
    recorded_at: datetime
    owner_id: int
    
    # --- ADDED FIELDS ---
    percentage: Optional[float] = None
    trend: Optional[str] = None
    source: Optional[str] = None

    class Config(ConfigBase):
        pass

class PEFRRecordResponse(BaseModel):
    zone: str
    guidance: str
    record: PEFRRecord
    
    # --- ADDED FIELDS ---
    percentage: Optional[float] = None
    trend: Optional[str] = None

# --- Symptom Schemas ---

class SymptomCreate(BaseModel):
    wheeze_rating: Optional[int] = None
    cough_rating: Optional[int] = None
    dust_exposure: Optional[bool] = False
    smoke_exposure: Optional[bool] = False
    
    # --- ADDED FIELDS ---
    dyspnea_rating: Optional[int] = None
    night_symptoms_rating: Optional[int] = None
    severity: Optional[str] = None
    onset_at: Optional[datetime] = None
    duration: Optional[int] = None # in minutes
    suspected_trigger: Optional[str] = None

class Symptom(SymptomCreate):
    id: int
    recorded_at: datetime
    owner_id: int

    class Config(ConfigBase):
        pass

# This line allows the User schema to find the BaselinePEFR schema
User.update_forward_refs()


# --- [START] NEW DOCTOR-PATIENT LINK SCHEMAS ---

class DoctorPatientLinkCreate(BaseModel):
    doctor_email: EmailStr

class DoctorPatientLink(BaseModel):
    id: int
    doctor_id: int
    patient_id: int

    class Config(ConfigBase):
        pass

# --- [END] NEW DOCTOR-PATIENT LINK SCHEMAS ---
