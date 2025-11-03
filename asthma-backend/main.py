# asthma-backend/main.py

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

import auth, database, models, schemas  # <-- CORRECTED IMPORT
from database import engine

# Create all database tables on startup
models.Base.metadata.create_all(bind=engine)

app = FastAPI()

# --- Utility Function ---

def calculate_zone(baseline: int, current_pefr: int):
    """
    Calculates the asthma zone based on PEFR.
    Returns (zone_name, guidance_message)
    """
    if baseline == 0:
        return ("Unknown", "Please set your baseline PEFR in your profile.")

    percentage = (current_pefr / baseline) * 100

    if percentage >= 80:
        return (
            "Green",
            "You are in the Green Zone. Continue with your regular treatment plan."
        )
    elif 50 <= percentage < 80:
        return (
            "Yellow",
            "You are in the Yellow Zone. Use your reliever inhaler. Follow your asthma action plan for the Yellow Zone."
        )
    else: # percentage < 50
        return (
            "Red",
            "You are in the Red Zone. This is a medical emergency. Use your reliever inhaler immediately and seek medical help."
        )

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
        role=user.role
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
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
    return {
        "access_token": access_token, 
        "token_type": "bearer",
        "user_role": user.role # Send role to the app
    }

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
        # Update existing baseline
        db_baseline.baseline_value = baseline.baseline_value
    else:
        # Create new baseline
        db_baseline = models.BaselinePEFR(**baseline.dict(), owner_id=current_user.id)
        db.add(db_baseline)
    
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

    zone, guidance = calculate_zone(baseline_value, pefr.pefr_value)
    
    db_record = models.PEFRRecord(
        pefr_value=pefr.pefr_value,
        zone=zone,
        owner_id=current_user.id
    )
    db.add(db_record)
    db.commit()
    db.refresh(db_record)
    
    return schemas.PEFRRecordResponse(
        zone=zone,
        guidance=guidance,
        record=db_record
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
    db.commit()
    db.refresh(db_symptom)
    return db_symptom