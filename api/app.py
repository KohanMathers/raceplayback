from flask import Flask, jsonify, request
import fastf1
import pandas as pd
import threading
import time
import requests
import json
import tempfile
import os
import importlib
import sqlite3
from datetime import datetime
from dotenv import load_dotenv

def normalize_session_type(session_type):
    session_map = {
        'r': 'Race',
        'race': 'Race',
        'q': 'Qualifying',
        'qualifying': 'Qualifying',
        'sprint': 'Sprint',
        's': 'Sprint',
        'fp1': 'Practice 1',
        'fp2': 'Practice 2',
        'fp3': 'Practice 3',
        'practice1': 'Practice 1',
        'practice2': 'Practice 2',
        'practice3': 'Practice 3',
    }
    return session_map.get(session_type.lower(), session_type)


def normalize_gp_name(gp, year):
    gp_map = {
        'bahrain': 'Bahrain Grand Prix',
        'saudi_arabia': 'Saudi Arabian Grand Prix',
        'australia': 'Australian Grand Prix',
        'japan': 'Japanese Grand Prix',
        'china': 'Chinese Grand Prix',
        'miami': 'Miami Grand Prix',
        'emilia_romagna': 'Emilia Romagna Grand Prix',
        'monaco': 'Monaco Grand Prix',
        'canada': 'Canadian Grand Prix',
        'spain': 'Spanish Grand Prix',
        'austria': 'Austrian Grand Prix',
        'silverstone': 'British Grand Prix',
        'hungary': 'Hungarian Grand Prix',
        'belgium': 'Belgian Grand Prix',
        'netherlands': 'Dutch Grand Prix',
        'italy': 'Italian Grand Prix',
        'azerbaijan': 'Azerbaijan Grand Prix',
        'singapore': 'Singapore Grand Prix',
        'texas': 'United States Grand Prix',
        'mexico': 'Mexico City Grand Prix',
        'brazil': 'São Paulo Grand Prix',
        'las_vegas': 'Las Vegas Grand Prix',
        'qatar': 'Qatar Grand Prix',
        'abu_dhabi': 'Abu Dhabi Grand Prix',
    }
    
    gp_lower = gp.lower()
    
    if gp_lower in gp_map:
        return gp_map[gp_lower]
    
    try:
        events = fastf1.get_event_schedule(year)
        for _, event in events.iterrows():
            event_name = event['EventName']
            if gp.lower() == event_name.lower():
                return event_name
            if gp.lower() in event_name.lower():
                return event_name
    except:
        pass
    
    return gp

app = Flask(__name__)

load_dotenv()
TRANSCRIBE_API_KEY = os.getenv('TRANSCRIBE_API_KEY')

whisper = importlib.import_module('whisper')
whisper_model = whisper.load_model("turbo")

DB_PATH = 'f1_data.db'

def init_db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA busy_timeout=5000')
    c = conn.cursor()
    
    c.execute('''CREATE TABLE IF NOT EXISTS sessions (
        sessionKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionID TEXT UNIQUE NOT NULL,
        year INTEGER,
        gp TEXT,
        session_type TEXT,
        date TEXT,
        circuit_name TEXT,
        location TEXT,
        number_of_laps INTEGER,
        event_name TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS drivers (
        driverKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        driver_code TEXT,
        driver_name TEXT,
        driver_number INTEGER,
        driver_abbreviation TEXT,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS laps (
        lapKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        driver TEXT,
        driver_code TEXT,
        lap_number INTEGER,
        lap_time REAL,
        sector1_time REAL,
        sector2_time REAL,
        sector3_time REAL,
        is_personal_best BOOLEAN,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS telemetry (
        telemetryKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        driver_code TEXT,
        lap_number INTEGER,
        session_time_ms REAL,
        x REAL,
        y REAL,
        gear INTEGER,
        distance REAL,
        speed INTEGER,
        throttle REAL,
        brake REAL,
        rpm INTEGER,
        drs INTEGER,
        compound TEXT,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS pit_stops (
        pitKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        driver TEXT,
        lap_number INTEGER,
        pit_in_time REAL,
        pit_out_time REAL,
        duration REAL,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS messages (
        messageKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        time REAL,
        category TEXT,
        message TEXT,
        status TEXT,
        flag TEXT,
        scope TEXT,
        sector TEXT,
        racing_number TEXT,
        lap INTEGER,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS weather (
        weatherKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        time REAL,
        rainfall REAL,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    c.execute('''CREATE TABLE IF NOT EXISTS radios (
        radioKey INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionKey INTEGER,
        timestamp TEXT,
        utc TEXT,
        racing_number TEXT,
        audio_url TEXT,
        transcript TEXT,
        FOREIGN KEY(sessionKey) REFERENCES sessions(sessionKey)
    )''')
    
    conn.commit()
    conn.close()

def get_or_create_session_key(year, gp, session_type):
    session = fastf1.get_session(year, gp, session_type)
    
    date_str = session.date.strftime('%d-%m-%Y')
    gp_upper = gp.upper()
    session_type_upper = session_type.upper()
    session_id = f"{date_str}-{gp_upper}-{session_type_upper}"
    
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT sessionKey FROM sessions WHERE sessionID = ?', (session_id,))
    result = c.fetchone()
    
    if result:
        conn.close()
        return result[0], session_id, False
    
    session.load()
    
    session_info = session.session_info
    c.execute('''INSERT INTO sessions 
                 (sessionID, year, gp, session_type, date, circuit_name, location, number_of_laps, event_name)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)''',
              (session_id, year, gp, session_type, str(session.date),
               session_info['Meeting']['Circuit']['ShortName'],
               session_info['Meeting']['Country']['Name'],
               session.total_laps,
               session_info['Meeting']['Name']))
    
    conn.commit()
    session_key = c.lastrowid
    conn.close()
    
    return session_key, session_id, True

def timedelta_to_seconds(td):
    if pd.isna(td):
        return None
    return td.total_seconds()

def get_session_path(year, gp, session_type):
    session = fastf1.get_session(year, gp, session_type)
    session_date = session.date.strftime('%Y-%m-%d')
    session_year = session.date.strftime('%Y')
    event_name = session.event['EventName'].replace(' ', '_')
    session_name = session.name.replace(' ', '_')
    path = f"{session_year}/{session_date}_{event_name}/{session_date}_{session_name}/"
    return path

def parse_jsonstream(content):
    lines = content.strip().split('\n')
    messages = []
    
    for line in lines:
        if not line.strip():
            continue
        parts = line.split('{', 1)
        if len(parts) < 2:
            continue
        timestamp = parts[0].strip()
        json_data = '{' + parts[1]
        
        try:
            data = json.loads(json_data)
            captures = data.get('Captures', {})
            
            if isinstance(captures, list):
                for capture in captures:
                    messages.append({
                        'timestamp': timestamp,
                        'utc': capture.get('Utc'),
                        'racing_number': capture.get('RacingNumber'),
                        'path': capture.get('Path')
                    })
            elif isinstance(captures, dict):
                for key, capture in captures.items():
                    messages.append({
                        'timestamp': timestamp,
                        'utc': capture.get('Utc'),
                        'racing_number': capture.get('RacingNumber'),
                        'path': capture.get('Path')
                    })
        except json.JSONDecodeError:
            continue
    
    return messages

def transcribe_audio(audio_url):
    try:
        response = requests.get(audio_url, timeout=30)
        response.raise_for_status()
        
        with tempfile.NamedTemporaryFile(delete=False, suffix='.mp3') as temp_audio:
            temp_audio.write(response.content)
            temp_audio_path = temp_audio.name
        
        try:
            if os.path.isfile(temp_audio_path):
                transcript = whisper_model.transcribe(temp_audio_path)['text']
                return transcript
        finally:
            if os.path.exists(temp_audio_path):
                os.remove(temp_audio_path)
    except Exception as e:
        print(f"Error transcribing audio from {audio_url}: {str(e)}")
        return None

def transcribe_session_radios(year, gp, session_type):
    try:
        session_key, _, _ = get_or_create_session_key(year, gp, session_type)
        session_path = get_session_path(year, gp, session_type)
        base_url = "https://livetiming.formula1.com/static/"
        jsonstream_url = f"{base_url}{session_path}TeamRadio.jsonStream"
        
        print(f"Fetching radio data for {year} {gp} {session_type}...")
        response = requests.get(jsonstream_url, timeout=30)
        response.raise_for_status()
        
        messages = parse_jsonstream(response.text)
        
        transcribed_count = 0
        for msg in messages:
            conn = sqlite3.connect(DB_PATH)
            c = conn.cursor()
            
            audio_url = f"{base_url}{session_path}{msg['path']}"
            
            c.execute('SELECT transcript FROM radios WHERE sessionKey = ? AND audio_url = ?', 
                     (session_key, audio_url))
            existing = c.fetchone()
            
            if existing and existing[0]:
                print(f"Already transcribed: {msg['racing_number']} - skipping")
                conn.close()
                continue
            
            print(f"Transcribing radio for driver {msg['racing_number']}...")
            transcript = transcribe_audio(audio_url)
            
            if existing:
                c.execute('UPDATE radios SET transcript = ? WHERE sessionKey = ? AND audio_url = ?',
                         (transcript, session_key, audio_url))
            else:
                c.execute('''INSERT INTO radios 
                            (sessionKey, timestamp, utc, racing_number, audio_url, transcript)
                            VALUES (?, ?, ?, ?, ?, ?)''',
                         (session_key, msg['timestamp'], msg['utc'], msg['racing_number'], audio_url, transcript))
            
            transcribed_count += 1
            conn.commit()
            conn.close()
        
        return {
            'year': year,
            'gp': gp,
            'session_type': session_type,
            'total_messages': len(messages),
            'transcribed': transcribed_count
        }
        
    except Exception as e:
        print(f"Error transcribing session {year} {gp} {session_type}: {str(e)}")
        return None

@app.route('/api/v1/transcribe/year', methods=['POST'])
def transcribe_year():
    api_key = request.headers.get('X-API-Key')
    
    if not api_key or api_key != TRANSCRIBE_API_KEY:
        return jsonify({"error": "Unauthorized - Invalid or missing API key"}), 401
    
    data = request.get_json()
    if not data or 'year' not in data:
        return jsonify({"error": "Year is required in request body"}), 400
    
    year = data['year']
    
    try:
        events = fastf1.get_event_schedule(year)
        results = []
        
        for _, event in events.iterrows():
            gp = event['EventName']
            
            for session_type in ['Race', 'Qualifying', 'Sprint']:
                try:
                    result = transcribe_session_radios(year, gp, session_type)
                    if result:
                        results.append(result)
                except Exception as e:
                    print(f"Skipping {gp} {session_type}: {str(e)}")
                    continue
        
        return jsonify({
            "year": year,
            "total_sessions_processed": len(results),
            "sessions": results
        })
        
    except Exception as e:
        return jsonify({"error": f"Error processing year {year}: {str(e)}"}), 500

@app.route('/api/v1/transcribe/gp', methods=['POST'])
def transcribe_gp():
    api_key = request.headers.get('X-API-Key')
    
    if not api_key or api_key != TRANSCRIBE_API_KEY:
        return jsonify({"error": "Unauthorized - Invalid or missing API key"}), 401
    
    data = request.get_json()
    if not data or 'year' not in data or 'gp' not in data:
        return jsonify({"error": "Year and gp are required in request body"}), 400
    
    year = data['year']
    gp = data['gp']
    
    try:
        normalized_gp = normalize_gp_name(gp, year)
        results = []
        
        for session_type in ['Race', 'Qualifying', 'Sprint']:
            try:
                result = transcribe_session_radios(year, normalized_gp, session_type)
                if result:
                    results.append(result)
            except Exception as e:
                print(f"Skipping {normalized_gp} {session_type}: {str(e)}")
                continue
        
        return jsonify({
            "year": year,
            "gp": normalized_gp,
            "total_sessions_processed": len(results),
            "sessions": results
        })
        
    except Exception as e:
        return jsonify({"error": f"Error processing {year} {gp}: {str(e)}"}), 500

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/drivers', methods=['GET'])
def get_drivers(year, gp, session_type):
    session_key, session_id, is_new = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT driver_code, driver_name FROM drivers WHERE sessionKey = ?', (session_key,))
    db_drivers = c.fetchall()
    
    if db_drivers:
        conn.close()
        return jsonify([{'code': d[0], 'name': d[1]} for d in db_drivers])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    drivers = session.drivers
    
    for driver_code in drivers:
        c.execute('INSERT INTO drivers (sessionKey, driver_code, driver_name) VALUES (?, ?, ?)',
                  (session_key, driver_code, driver_code))
    
    conn.commit()
    conn.close()
    return jsonify([{'code': d} for d in drivers])

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/drivers/<int:number>', methods=['GET'])
def get_driver_by_number(year, gp, session_type, number):
    session_key, session_id, is_new = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()

    c.execute('SELECT driver_code, driver_name, driver_abbreviation FROM drivers WHERE sessionKey = ? AND driver_number = ?', 
              (session_key, number))
    result = c.fetchone()
    
    if result:
        conn.close()
        return jsonify({'code': result[0], 'name': result[1], 'abbreviation': result[2]})
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    
    driver_info = None
    for driver_code in session.drivers:
        driver = session.get_driver(driver_code)
        if str(driver['DriverNumber']) == str(number):
            driver_info = {'code': driver_code, 'name': driver['FullName'], 'abbreviation': driver['Abbreviation']}
            c.execute('INSERT INTO drivers (sessionKey, driver_code, driver_name, driver_number, driver_abbreviation) VALUES (?, ?, ?, ?, ?)',
                      (session_key, driver_code, driver['FullName'], number, driver['Abbreviation']))
            break
    
    conn.commit()
    conn.close()
    
    if driver_info:
        return jsonify(driver_info)
    else:
        return jsonify({'error': 'Driver not found'}), 404


@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/telemetry/<driver_code>/<int:lap>', methods=['GET'])
def get_telemetry(year, gp, session_type, driver_code, lap):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('''SELECT * FROM telemetry 
                 WHERE sessionKey = ? AND driver_code = ? AND lap_number = ?
                 ORDER BY session_time_ms''',
              (session_key, driver_code, lap))
    
    db_telemetry = c.fetchall()
    
    if db_telemetry:
        conn.close()
        columns = ['session_time_ms', 'x', 'y', 'gear', 'distance', 'speed', 'throttle', 'brake', 'rpm', 'drs', 'compound']
        telemetry_list = [dict(zip(columns, row[4:])) for row in db_telemetry]
        return jsonify([{'lap_number': lap, 'telemetry': telemetry_list}])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    laps = session.laps.pick_drivers(driver_code).pick_laps(lap)
    
    if laps.empty:
        conn.close()
        return jsonify({"error": "Driver not found"}), 404
    
    telemetry_data = []
    for _, lap_row in laps.iterrows():
        tyre = lap_row['Compound']
        telemetry = lap_row.get_telemetry()
        telemetry['Compound'] = tyre
        telemetry['SessionTime_ms'] = telemetry['SessionTime'].dt.total_seconds() * 1000
        
        for _, tel_row in telemetry.iterrows():
            c.execute('''INSERT INTO telemetry 
                        (sessionKey, driver_code, lap_number, session_time_ms, x, y, gear, distance, speed, throttle, brake, rpm, drs, compound)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                      (session_key, driver_code, lap_row.LapNumber, tel_row['SessionTime_ms'], tel_row['X'], tel_row['Y'],
                       tel_row['nGear'], tel_row['Distance'], tel_row['Speed'], tel_row['Throttle'], tel_row['Brake'],
                       tel_row['RPM'], tel_row['DRS'], tyre))
        
        telemetry_data.append({
            "lap_number": lap_row.LapNumber,
            "telemetry": telemetry[['SessionTime_ms', 'X', 'Y', 'nGear', 'Distance', 'Speed', 'Throttle', 'Brake', 'RPM', 'DRS', 'Compound']].to_dict(orient='records')
        })
    
    conn.commit()
    conn.close()
    return jsonify(telemetry_data)

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/info', methods=['GET'])
def get_session_info(year, gp, session_type):
    session_key, session_id, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT * FROM sessions WHERE sessionKey = ?', (session_key,))
    result = c.fetchone()
    conn.close()
    
    if result:
        return jsonify({
            "year": result[2],
            "grand_prix": result[3],
            "session_type": result[4],
            "date": result[5],
            "circuit_name": result[6],
            "location": result[7],
            "number_of_laps": result[8]
        })
    
    return jsonify({"error": "Session not found"}), 404

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/laps', methods=['GET'])
def get_laps(year, gp, session_type):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT * FROM laps WHERE sessionKey = ?', (session_key,))
    db_laps = c.fetchall()
    
    if db_laps:
        conn.close()
        columns = ['driver', 'driver_code', 'lap_number', 'lap_time', 'sector1_time', 'sector2_time', 'sector3_time', 'is_personal_best']
        return jsonify([dict(zip(columns, lap[2:])) for lap in db_laps])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    laps = session.laps
    laps_df = laps[['Driver', 'DriverNumber', 'LapNumber', 'LapTime', 'Sector1Time', 'Sector2Time', 'Sector3Time', 'IsPersonalBest']].copy()
    
    laps_df['LapTime'] = laps_df['LapTime'].apply(timedelta_to_seconds)
    laps_df['Sector1Time'] = laps_df['Sector1Time'].apply(timedelta_to_seconds)
    laps_df['Sector2Time'] = laps_df['Sector2Time'].apply(timedelta_to_seconds)
    laps_df['Sector3Time'] = laps_df['Sector3Time'].apply(timedelta_to_seconds)
    
    for _, lap_row in laps_df.iterrows():
        driver_code = int(lap_row['DriverNumber'])
        c.execute('''INSERT INTO laps 
                    (sessionKey, driver, driver_code, lap_number, lap_time, sector1_time, sector2_time, sector3_time, is_personal_best)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                  (session_key, lap_row['Driver'], driver_code, int(lap_row['LapNumber']), lap_row['LapTime'],
                   lap_row['Sector1Time'], lap_row['Sector2Time'], lap_row['Sector3Time'], int(lap_row['IsPersonalBest'])))
    
    conn.commit()
    conn.close()
    return jsonify(laps_df.to_dict(orient='records'))


def get_driver_laps(year, gp, session_type, driver_code):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    driver_code_int = int(driver_code) if isinstance(driver_code, str) and driver_code.isdigit() else driver_code
    
    c.execute('SELECT * FROM laps WHERE sessionKey = ? AND driver_code = ?', (session_key, driver_code_int))
    db_laps = c.fetchall()
    
    if db_laps:
        conn.close()
        columns = ['driver', 'driver_code', 'lap_number', 'lap_time', 'sector1_time', 'sector2_time', 'sector3_time', 'is_personal_best']
        return jsonify([dict(zip(columns, lap[2:])) for lap in db_laps])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    laps = session.laps.pick_drivers(driver_code)
    
    if laps.empty:
        conn.close()
        return jsonify({"error": "Driver not found"}), 404
    
    laps_df = laps[['Driver', 'DriverNumber', 'LapNumber', 'LapTime', 'Sector1Time', 'Sector2Time', 'Sector3Time', 'IsPersonalBest']].copy()
    laps_df['LapTime'] = laps_df['LapTime'].apply(timedelta_to_seconds)
    laps_df['Sector1Time'] = laps_df['Sector1Time'].apply(timedelta_to_seconds)
    laps_df['Sector2Time'] = laps_df['Sector2Time'].apply(timedelta_to_seconds)
    laps_df['Sector3Time'] = laps_df['Sector3Time'].apply(timedelta_to_seconds)
    
    for _, lap_row in laps_df.iterrows():
        driver_code_int = int(lap_row['DriverNumber'])
        c.execute('''INSERT INTO laps 
                    (sessionKey, driver, driver_code, lap_number, lap_time, sector1_time, sector2_time, sector3_time, is_personal_best)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                  (session_key, lap_row['Driver'], driver_code_int, int(lap_row['LapNumber']), lap_row['LapTime'],
                   lap_row['Sector1Time'], lap_row['Sector2Time'], lap_row['Sector3Time'], int(lap_row['IsPersonalBest'])))
    
    conn.commit()
    conn.close()
    return jsonify(laps_df.to_dict(orient='records'))

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/laps/<driver_code>', methods=['GET'])
def get_driver_laps(year, gp, session_type, driver_code):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT * FROM laps WHERE sessionKey = ? AND driver_code = ?', (session_key, driver_code))
    db_laps = c.fetchall()
    
    if db_laps:
        conn.close()
        columns = ['driver', 'driver_code', 'lap_number', 'lap_time', 'sector1_time', 'sector2_time', 'sector3_time', 'is_personal_best']
        return jsonify([dict(zip(columns, lap[2:])) for lap in db_laps])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    laps = session.laps.pick_drivers(driver_code)
    
    if laps.empty:
        conn.close()
        return jsonify({"error": "Driver not found"}), 404
    
    laps_df = laps[['Driver', 'LapNumber', 'LapTime', 'Sector1Time', 'Sector2Time', 'Sector3Time', 'IsPersonalBest']].copy()
    laps_df['LapTime'] = laps_df['LapTime'].apply(timedelta_to_seconds)
    laps_df['Sector1Time'] = laps_df['Sector1Time'].apply(timedelta_to_seconds)
    laps_df['Sector2Time'] = laps_df['Sector2Time'].apply(timedelta_to_seconds)
    laps_df['Sector3Time'] = laps_df['Sector3Time'].apply(timedelta_to_seconds)
    
    for _, lap_row in laps_df.iterrows():
        c.execute('''INSERT INTO laps 
                    (sessionKey, driver, driver_code, lap_number, lap_time, sector1_time, sector2_time, sector3_time, is_personal_best)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                  (session_key, lap_row['Driver'], driver_code, lap_row['LapNumber'], lap_row['LapTime'],
                   lap_row['Sector1Time'], lap_row['Sector2Time'], lap_row['Sector3Time'], lap_row['IsPersonalBest']))
    
    conn.commit()
    conn.close()
    return jsonify(laps_df.to_dict(orient='records'))

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/pits', methods=['GET'])
def get_pit_stops(year, gp, session_type):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT * FROM pit_stops WHERE sessionKey = ?', (session_key,))
    db_pits = c.fetchall()
    
    if db_pits:
        conn.close()
        columns = ['driver', 'lap_number', 'pit_in_time', 'pit_out_time', 'duration']
        return jsonify([dict(zip(columns, pit[2:])) for pit in db_pits])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load()
    laps = session.laps.sort_values(['Driver', 'LapNumber'])
    pit_stops = laps[laps['PitOutTime'].notna()].copy()
    pit_stops['PitInTime'] = laps.groupby('Driver')['PitInTime'].shift(1).loc[pit_stops.index]
    pit_stops['Duration'] = pit_stops['PitOutTime'] - pit_stops['PitInTime']
    pit_stops_df = pit_stops[['Driver', 'LapNumber', 'PitInTime', 'PitOutTime', 'Duration']].copy()
    pit_stops_df['PitInTime'] = pit_stops_df['PitInTime'].apply(timedelta_to_seconds)
    pit_stops_df['PitOutTime'] = pit_stops_df['PitOutTime'].apply(timedelta_to_seconds)
    pit_stops_df['Duration'] = pit_stops_df['Duration'].apply(timedelta_to_seconds)
    
    for _, pit_row in pit_stops_df.iterrows():
        c.execute('''INSERT INTO pit_stops 
                    (sessionKey, driver, lap_number, pit_in_time, pit_out_time, duration)
                    VALUES (?, ?, ?, ?, ?, ?)''',
                  (session_key, pit_row['Driver'], pit_row['LapNumber'], pit_row['PitInTime'],
                   pit_row['PitOutTime'], pit_row['Duration']))
    
    conn.commit()
    conn.close()
    return jsonify(pit_stops_df.to_dict(orient='records'))

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/messages', methods=['GET'])
def get_messages(year, gp, session_type):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT * FROM messages WHERE sessionKey = ?', (session_key,))
    db_messages = c.fetchall()
    
    if db_messages:
        conn.close()
        columns = ['time', 'category', 'message', 'status', 'flag', 'scope', 'sector', 'racing_number', 'lap']
        return jsonify([dict(zip(columns, msg[2:])) for msg in db_messages])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load(messages=True)
    messages = session.race_control_messages
    messages_df = messages[['Time', 'Category', 'Message', 'Status', 'Flag', 'Scope', 'Sector', 'RacingNumber', 'Lap']].copy()
    
    for _, msg_row in messages_df.iterrows():
        c.execute('''INSERT INTO messages 
                    (sessionKey, time, category, message, status, flag, scope, sector, racing_number, lap)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                  (session_key, str(msg_row['Time']), msg_row['Category'], msg_row['Message'],
                   msg_row['Status'], msg_row['Flag'], msg_row['Scope'], msg_row['Sector'], msg_row['RacingNumber'], msg_row['Lap']))
    
    conn.commit()
    conn.close()
    return jsonify(messages_df.to_dict(orient='records'))

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/weather', methods=['GET'])
def get_weather(year, gp, session_type):
    session_key, _, _ = get_or_create_session_key(year, gp, session_type)
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    c.execute('SELECT * FROM weather WHERE sessionKey = ?', (session_key,))
    db_weather = c.fetchall()
    
    if db_weather:
        conn.close()
        columns = ['time', 'rainfall']
        return jsonify([dict(zip(columns, w[2:])) for w in db_weather])
    
    session = fastf1.get_session(year, gp, session_type)
    session.load(weather=True)
    weather = session.weather_data
    weather_df = weather[['Time', 'Rainfall']].copy()
    weather_df['Time'] = weather_df['Time'].apply(timedelta_to_seconds)
    
    for _, weather_row in weather_df.iterrows():
        c.execute('INSERT INTO weather (sessionKey, time, rainfall) VALUES (?, ?, ?)',
                  (session_key, weather_row['Time'], weather_row['Rainfall']))
    
    conn.commit()
    conn.close()
    return jsonify(weather_df.to_dict(orient='records'))

@app.route('/api/v1/sessions/<int:year>/events', methods=['GET'])
def get_events(year):
    events = fastf1.get_event_schedule(year)
    events_df = events[['RoundNumber', 'EventName', 'EventDate']].copy()
    return jsonify(events_df.to_dict(orient='records'))

@app.route('/api/v1/sessions/<int:year>/<gp>/<session_type>/radios', methods=['GET'])
def get_radios(year, gp, session_type):
    try:
        normalized_session_type = normalize_session_type(session_type)
        normalized_gp = normalize_gp_name(gp, year)
        session_key, _, _ = get_or_create_session_key(year, normalized_gp, normalized_session_type)
        conn = sqlite3.connect(DB_PATH)
        c = conn.cursor()
        
        driver_filter = request.args.get('driver', None)
        
        query = 'SELECT * FROM radios WHERE sessionKey = ?'
        params = [session_key]
        
        if driver_filter:
            query += ' AND racing_number = ?'
            params.append(driver_filter)
        
        c.execute(query, params)
        db_radios = c.fetchall()
        conn.close()
        
        if not db_radios:
            return jsonify({'total_messages': 0, 'messages': []})
        
        columns = ['timestamp', 'utc', 'racing_number', 'audio_url', 'transcript']
        results = [dict(zip(columns, radio[2:])) for radio in db_radios]
        
        return jsonify({'total_messages': len(results), 'messages': results})
        
    except Exception as e:
        return jsonify({"error": f"Error fetching radio data: {str(e)}"}), 500

if __name__ == '__main__':
    init_db()
    app.run(host='0.0.0.0', port=5000)