# asthma-backend/main.py

from fastapi import FastAPI, Depends, HTTPException, status, Query
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from sqlalchemy import select # <-- FIX: 'select' is imported from 'sqlalchemy'
from typing import List, Optional
import datetime

import auth, database, models, schemas
from database import engine

# Create all database tables on startup
models.Base.metadata.create_all(bind=engine)

app = FastAPI()

# --- Utility Functions ---

def calculate_zone(baseline: int, current_pefr: int):
    """
    Calculates the asthma zone based on PEFR.
    Returns (zone_name, guidance_message, percentage)
    """
    if baseline == 0:
        return ("Unknown", "Please set your baseline PEFR in your profile.", 0.0)

    percentage = (current_pefr / baseline) * 100

    if percentage >= 80:
        return (
            "Green",
            "You are in the Green Zone. Continue with your regular treatment plan.",
            percentage
        )
    elif 50 <= percentage < 80:
        return (
            "Yellow",
            "You are in the Yellow Zone. Use your reliever inhaler. Follow your asthma action plan for the Yellow Zone.",
            percentage
        )
    else: # percentage < 50
        return (
            "Red",
            "You are in the Red Zone. This is a medical emergency. Use your reliever inhaler immediately and seek medical help.",
            percentage
        )

def get_pefr_trend(db: Session, owner_id: int, current_pefr: int):
    """
    Determines if the trend is improving, stable, or worsening.
    """
    last_record = db.query(models.PEFRRecord).filter(
        models.PEFRRecord.owner_id == owner_id
    ).order_by(models.PEFRRecord.recorded_at.desc()).first()
    
    if not last_record:
        return "stable" # First record
    
    if current_pefr > last_record.pefr_value:
        return "improving"
    elif current_pefr < last_record.pefr_value:
        return "worsening"
    else:
        return "stable"

def log_audit(db: Session, user_id: int, action: str, details: str = None):
    """
    Helper function to create an audit log entry.
    """
    db_log = models.AuditLog(user_id=user_id, action=action, details=details)
    db.add(db_log)
    
def log_alert(db: Session, user_id: int, alert_type: str):
    """
    Helper function to create an alert log entry.
    """
    db_alert = models.AlertLog(user_id=user_id, alert_type=alert_type)
    db.add(db_alert)

# --- Endpoints ---

@app.get("/")
def read_root():
    return {"message": "Welcome to the Asthma Manager API"}

# --- Authentication Endpoints ---

@app.post("/auth/signup", response_model=schemas.User)
def signup(user: schemas.UserCreate, db: Session = Depends(database.get_db)):
    db_user = auth.get_user(db, email=user.email)
    if db_user:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    hashed_password = auth.get_password_hash(user.password)
    
    db_user = models.User(
        email=user.email,
        name=user.name,
        hashed_password=hashed_password,
        role=user.role,
        age=user.age,
        height=user.height,
        gender=user.gender,
        contact_number=user.contact_number,
        address=user.address
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    
    log_audit(db, db_user.id, "SIGNUP", f"User {user.email} registered as {user.role}")
    db.commit()
    
    return db_user

@app.post("/auth/login", response_model=schemas.Token)
def login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(database.get_db)):
    user = auth.get_user(db, email=form_data.username) # username is the email
    
    if not user or not auth.verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    access_token = auth.create_access_token(
        data={"sub": user.email}
    )
    
    log_audit(db, user.id, "LOGIN", f"User {user.email} logged in.")
    db.commit()
    
    return {
        "access_token": access_token, 
        "token_type": "bearer",
        "user_role": user.role
    }

# --- Profile Management Endpoints ---

@app.get("/profile/me", response_model=schemas.User)
def get_my_profile(current_user: models.User = Depends(auth.get_current_user)):
    return current_user

@app.put("/profile/me", response_model=schemas.User)
def update_my_profile(
    profile_update: schemas.UserCreate, 
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(auth.get_current_user)
):
    current_user.name = profile_update.name
    current_user.age = profile_update.age
    current_user.height = profile_update.height
    current_user.gender = profile_update.gender
    current_user.contact_number = profile_update.contact_number
    current_user.address = profile_update.address
    
    if profile_update.password:
        current_user.hashed_password = auth.get_password_hash(profile_update.password)
        
    db.commit()
    db.refresh(current_user)
    log_audit(db, current_user.id, "UPDATE_PROFILE")
    db.commit()
    return current_user

# --- Asthma Management Endpoints ---

@app.post("/patient/baseline", response_model=schemas.BaselinePEFR)
def set_baseline(
    baseline: schemas.BaselinePEFRCreate, 
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    if current_user.role != models.UserRole.PATIENT:
        raise HTTPException(status_code=403, detail="Only patients can set a baseline.")
    
    db_baseline = db.query(models.BaselinePEFR).filter(models.BaselinePEFR.owner_id == current_user.id).first()
    
    if db_baseline:
        db_baseline.baseline_value = baseline.baseline_value
        log_audit(db, current_user.id, "UPDATE_BASELINE", f"Value: {baseline.baseline_value}")
    else:
        db_baseline = models.BaselinePEFR(**baseline.dict(), owner_id=current_user.id)
        db.add(db_baseline)
        log_audit(db, current_user.id, "CREATE_BASELINE", f"Value: {baseline.baseline_value}")
    
    db.commit()
    db.refresh(db_baseline)
    return db_baseline

@app.post("/pefr/record", response_model=schemas.PEFRRecordResponse)
def record_pefr(
    pefr: schemas.PEFRRecordCreate,
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    if current_user.role != models.UserRole.PATIENT:
        raise HTTPException(status_code=403, detail="Only patients can record PEFR.")

    baseline = db.query(models.BaselinePEFR).filter(models.BaselinePEFR.owner_id == current_user.id).first()
    
    baseline_value = 0
    if baseline:
        baseline_value = baseline.baseline_value

    zone, guidance, percentage = calculate_zone(baseline_value, pefr.pefr_value)
    trend = get_pefr_trend(db, current_user.id, pefr.pefr_value)
    
    db_record = models.PEFRRecord(
        pefr_value=pefr.pefr_value,
        zone=zone,
        owner_id=current_user.id,
        percentage=percentage,
        trend=trend,
        source=pefr.source
    )
    db.add(db_record)
    
    if zone == "Red":
        log_alert(db, current_user.id, "RED_ZONE_TRIGGERED")
    
    log_audit(db, current_user.id, "RECORD_PEFR", f"Value: {pefr.pefr_value}, Zone: {zone}")
    
    db.commit()
    db.refresh(db_record)
    
    return schemas.PEFRRecordResponse(
        zone=zone,
        guidance=guidance,
        record=db_record,
        percentage=percentage,
        trend=trend
    )

@app.post("/symptom/record", response_model=schemas.Symptom)
def record_symptom(
    symptom: schemas.SymptomCreate,
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    if current_user.role != models.UserRole.PATIENT:
        raise HTTPException(status_code=403, detail="Only patients can record symptoms.")
        
    db_symptom = models.Symptom(
        **symptom.dict(),
        owner_id=current_user.id
    )
    db.add(db_symptom)
    log_audit(db, current_user.id, "RECORD_SYMPTOM")
    db.commit()
    db.refresh(db_symptom)
    return db_symptom


# --- [START] NEW ENDPOINT TO LINK PATIENT TO DOCTOR ---

@app.post("/patient/link-doctor", response_model=schemas.DoctorPatientLink)
def link_patient_to_doctor(
    link_request: schemas.DoctorPatientLinkCreate,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(auth.get_current_user)
):
    # 1. Ensure the current user is a Patient
    if current_user.role != models.UserRole.PATIENT:
        raise HTTPException(status_code=403, detail="Only patients can link to a doctor.")

    # 2. Find the doctor by the provided email
    doctor = db.query(models.User).filter(
        models.User.email == link_request.doctor_email,
        models.User.role == models.UserRole.DOCTOR
    ).first()

    if not doctor:
        raise HTTPException(status_code=404, detail="Doctor not found with that email.")

    # 3. Check if the link already exists
    existing_link = db.query(models.DoctorPatient).filter(
        models.DoctorPatient.doctor_id == doctor.id,
        models.DoctorPatient.patient_id == current_user.id
    ).first()

    if existing_link:
        # Link already exists, just return it
        return existing_link

    # 4. Create the new link
    db_link = models.DoctorPatient(
        doctor_id=doctor.id,
        patient_id=current_user.id
    )
    db.add(db_link)
    db.commit()
    db.refresh(db_link)

    log_audit(db, current_user.id, "LINK_DOCTOR", f"Patient linked to doctor ID {doctor.id}")
    db.commit()

    return db_link

# --- [END] NEW ENDPOINT TO LINK PATIENT TO DOCTOR ---


# --- NEW: Medication Endpoints ---

@app.post("/medications", response_model=schemas.Medication)
def create_medication(
    medication: schemas.MedicationCreate,
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    db_medication = models.Medication(**medication.dict(), owner_id=current_user.id)
    db.add(db_medication)
    db.commit()
    db.refresh(db_medication)
    return db_medication

@app.get("/medications", response_model=List[schemas.Medication])
def get_my_medications(
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    return db.query(models.Medication).filter(models.Medication.owner_id == current_user.id).all()

# --- NEW: Emergency Contact Endpoints ---

@app.post("/contacts", response_model=schemas.EmergencyContact)
def create_emergency_contact(
    contact: schemas.EmergencyContactCreate,
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    db_contact = models.EmergencyContact(**contact.dict(), owner_id=current_user.id)
    db.add(db_contact)
    db.commit()
    db.refresh(db_contact)
    return db_contact

@app.get("/contacts", response_model=List[schemas.EmergencyContact])
def get_my_emergency_contacts(
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    return db.query(models.EmergencyContact).filter(models.EmergencyContact.owner_id == current_user.id).all()

# --- NEW: Reminder Endpoints ---

@app.post("/reminders", response_model=schemas.Reminder)
def create_reminder(
    reminder: schemas.ReminderCreate,
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    db_reminder = models.Reminder(**reminder.dict(), owner_id=current_user.id)
    db.add(db_reminder)
    db.commit()
    db.refresh(db_reminder)
    return db_reminder

@app.get("/reminders", response_model=List[schemas.Reminder])
def get_my_reminders(
    db: Session = Depends(database.get_db), 
    current_user: models.User = Depends(auth.get_current_user)
):
    return db.query(models.Reminder).filter(models.Reminder.owner_id == current_user.id).all()

# --- NEW: Doctor Dashboard Endpoint ---

@app.get("/doctor/patients", response_model=List[schemas.User])
def get_doctor_patients(
    search: Optional[str] = Query(None, description="Search by patient name or email"),
    zone: Optional[str] = Query(None, description="Filter by current risk zone (Red, Yellow, Green)"),
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(auth.get_current_user)
):
    if current_user.role != models.UserRole.DOCTOR:
        raise HTTPException(status_code=403, detail="Only doctors can access this endpoint.")
    
    # Base query: Get IDs of patients linked to this doctor
    patient_links = select(models.DoctorPatient.patient_id).filter(
        models.DoctorPatient.doctor_id == current_user.id
    )

    query = db.query(models.User).filter(models.User.id.in_(patient_links))
    
    if search:
        query = query.filter(
            (models.User.name.ilike(f"%{search}%")) |
            (models.User.email.ilike(f"%{search}%"))
        )
    
    if zone:
        # This is a simplified placeholder.
        query = query.join(models.PEFRRecord).filter(models.PEFRRecord.zone == zone)

    return query.all()


# --- [START] NEW DOCTOR ENDPOINTS ---

def get_patient_by_id(db: Session, patient_id: int):
    """Helper to get a patient by ID"""
    return db.query(models.User).filter(models.User.id == patient_id, models.User.role == models.UserRole.PATIENT).first()

@app.get("/patient/{patient_id}/pefr", response_model=List[schemas.PEFRRecord])
def get_patient_pefr_records(
    patient_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(auth.get_current_user)
):
    if current_user.role != models.UserRole.DOCTOR:
        raise HTTPException(status_code=403, detail="Only doctors can access this data.")
    
    # TODO: Add check to ensure this doctor is linked to this patient
    
    patient = get_patient_by_id(db, patient_id)
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
        
    return db.query(models.PEFRRecord).filter(models.PEFRRecord.owner_id == patient_id).all()


@app.get("/patient/{patient_id}/symptoms", response_model=List[schemas.Symptom])
def get_patient_symptom_records(
    patient_id: int,
    db: Session = Depends(database.get_db),
    current_user: models.User = Depends(auth.get_current_user)
):
    if current_user.role != models.UserRole.DOCTOR:
        raise HTTPException(status_code=403, detail="Only doctors can access this data.")
    
    # TODO: Add check to ensure this doctor is linked to this patient

    patient = get_patient_by_id(db, patient_id)
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found.")
        
    return db.query(models.Symptom).filter(models.Symptom.owner_id == patient_id).all()

# --- [END] NEW DOCTOR ENDPOINTS ---
