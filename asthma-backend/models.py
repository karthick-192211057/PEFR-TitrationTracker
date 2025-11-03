# asthma-backend/models.py

from sqlalchemy import Boolean, Column, ForeignKey, Integer, String, Float, DateTime, Enum as SAEnum
from sqlalchemy.orm import relationship
from database import Base  # <-- CORRECTED IMPORT
import datetime
import enum

class UserRole(str, enum.Enum):
    PATIENT = "Patient"
    DOCTOR = "Doctor"

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, unique=True, index=True, nullable=False)
    name = Column(String, nullable=False)
    hashed_password = Column(String, nullable=False)
    role = Column(SAEnum(UserRole), nullable=False)
    
    # Relationships
    baseline = relationship("BaselinePEFR", back_populates="owner", uselist=False)
    pefr_records = relationship("PEFRRecord", back_populates="owner")
    symptoms = relationship("Symptom", back_populates="owner")

class BaselinePEFR(Base):
    __tablename__ = "baseline_pefr"

    id = Column(Integer, primary_key=True, index=True)
    baseline_value = Column(Integer, nullable=False)
    owner_id = Column(Integer, ForeignKey("users.id"))

    owner = relationship("User", back_populates="baseline")

class PEFRRecord(Base):
    __tablename__ = "pefr_records"

    id = Column(Integer, primary_key=True, index=True)
    pefr_value = Column(Integer, nullable=False)
    zone = Column(String, nullable=False)
    recorded_at = Column(DateTime, default=datetime.datetime.utcnow)
    owner_id = Column(Integer, ForeignKey("users.id"))

    owner = relationship("User", back_populates="pefr_records")

class Symptom(Base):
    __tablename__ = "symptoms"
    
    id = Column(Integer, primary_key=True, index=True)
    wheeze_rating = Column(Integer) # Scale 1-10
    cough_rating = Column(Integer)  # Scale 1-10
    dust_exposure = Column(Boolean, default=False)
    smoke_exposure = Column(Boolean, default=False)
    recorded_at = Column(DateTime, default=datetime.datetime.utcnow)
    owner_id = Column(Integer, ForeignKey("users.id"))

    owner = relationship("User", back_populates="symptoms")

class DoctorPatient(Base):
    __tablename__ = "doctor_patient_map"
    
    id = Column(Integer, primary_key=True, index=True)
    doctor_id = Column(Integer, ForeignKey("users.id"))
    patient_id = Column(Integer, ForeignKey("users.id"))