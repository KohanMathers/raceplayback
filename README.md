# RacePlayback

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Python](https://img.shields.io/badge/Python-3.11+-green.svg)](https://www.python.org/)
[![Minestom](https://img.shields.io/badge/Minestom-Latest-purple.svg)](https://minestom.net/)

RacePlayback is an ambitious F1 replay ecosystem combining:

* Python API server for session data, telemetry, and team radio
* Minestom server for 1:1 scaled Minecraft race replays
* Full caching and timeline syncing for accurate multi-driver replay

This project turns publicly available F1 telemetry and radio feeds into a fully interactive, immersive replay experience inside Minecraft.

---

## Features

### Telemetry & Race Data

* Get drivers from a session
* Fetch all laps of a session or a specific driver
* Access per-lap telemetry: `Distance`, `Speed`, `Throttle`, `Brake`, `RPM`, `DRS`
* Pit stop info with duration calculated automatically from entry/exit
* Session info: type, length, weather, timestamps
* Race control messages

### Weather

* Timestamps in a session include rain info, perfect for dynamic replay effects

### Events & Season Data

* All race events from a year available for queries

### Team Radio (Whisper-Powered)

* Fully transcribed radio messages for each race
* Messages appear in chat:

```
[TEAM RADIO] (VER) Box box
[TEAM RADIO] (HAM) Watch out for debris
```

* Synced with session timestamps for realistic replay

### Minestom Replay Server

* Entities represent F1 cars at 1:1 scale
* Smooth movement using telemetry
* Multi-driver support with perfectly synced timelines
* Optional gear/RPM/DRS display for extra realism

---

## Installation

1. Clone the repository

```bash
git clone https://github.com/kohanmathers/raceplayback.git
cd raceplayback
```

2. Set up Python API

```bash
cd api
python -m venv venv
source venv/bin/activate  # Linux/macOS
venv\Scripts\activate     # Windows
pip install -r requirements.txt
```

3. Run the API

```bash
python main.py
```

4. Set up Minestom server

```bash
cd minestom-server
./gradlew run
```

5. Configure API endpoint in Minestom config to point to your running Python server

---

## Architecture

```
Python API Server                  Minestom Server
----------------                  ----------------
- Session caching                  - Display entities
- Telemetry fetch & processing     - Timeline playback engine
- Team radio transcription          - Movement interpolation
- Pit stop & race control          - Multi-driver sync
- Event & weather info
- DB cache for all sessions
```

* Caching: Redis-free, all session data stored in a DB for fast repeated access
* Team radios: Downloaded as MP3 → transcribed via Whisper → added to JSON → temp file deleted

---

## Notes

* Only race sessions are fully supported for team radio
* Older sessions (pre-2018) may have limited telemetry

---

## Roadmap

* Multi-series support beyond F1 (NASCAR, IndyCar, etc.)
* Optional radio audio playback inside Minecraft
* Improved interpolation for smoother cornering
* Live session support for real-time telemetry sync

---

## 🙏 Credits & Acknowledgments

### Development
- Kohan Mathers - Project creator, lead developer, server architecture
  - Custom wheel models (all tire compounds, front & rear)
  - Steering wheel model
  - Rear wing/DRS flap model
  - Telemetry playback system
  - Minestom server implementation

### F1 Car Models
- AdamixGaming, Jerpix, NotGatoz - Original F1 car chassis models
  - [Download on Modrinth](https://modrinth.com/user/AdamixGaming/resourcepacks)
  - [Download on PlanetMinecraft](https://www.planetminecraft.com/member/adamixgaming/)
  - Modified for RaceReplay use (wheels removed for separate rotation entities)

### Data & APIs
- FastF1 - Python library for F1 telemetry data access
  - [FastF1 Documentation](https://docs.fastf1.dev/)
- FIA (Fédération Internationale de l'Automobile) - Original telemetry data and team radio API
  - Race timing data
  - GPS telemetry
  - Team radio audio

### Special Thanks
- Minestom Team - For the amazing server framework that makes this possible
- F1 Community - For inspiring this project and keeping the passion alive

---

## Contributing

PRs welcome, but know this repo is messy, ambitious, and a little chaotic. If you understand FastF1, Minestom, and Whisper, you’re in the club.

---

## License

This project is a non-commercial, educational fan project created for the F1 community. All F1-related data, team names, and driver names are property of Formula One World Championship Limited and the FIA.

Car models used with permission from original creators. Modified versions are used exclusively within this project.

MIT License — do whatever, just don’t blame me when you fall asleep watching pixelated F1 cars.