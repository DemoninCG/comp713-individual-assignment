# Clinic Appointment System

COMP713 Assignment 2 (Option A, Distributed Web/API Application).

A web/API application for a clinic reception. Staff can register patients and book, reschedule or cancel appointments from two browser pages. A REST API applies the scheduling rules and stores the data in a relational database.

The system has three parts:

1. Browser client (two HTML pages with plain JavaScript)
2. Spring Boot REST API (controllers, services and repositories)
3. Embedded H2 database (tables for patients and appointments)

## Requirements (software and tools)

- JDK 21 (Eclipse Temurin or OpenJDK)
- PowerShell for the commands below (any other shell works too)
- `curl.exe` for the API tests below (included with Windows 10 and 11)
- Optional: Visual Studio Code with the REST Client extension, to use `requests/clinic.http`

No other installation is needed. The Maven Wrapper (`mvnw.cmd`) and an embedded database are included in this folder.

## Setup

1. Download or clone this project folder.
2. Run the start command below. The first build downloads the Maven dependencies.

## Starting the system

```powershell
cd comp713-individual-assignment
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080> in a browser (this is the patient registry page).

- The service listens on port 8080. Set the environment variable `SERVER_PORT` to change it.
- Demo data is loaded at startup (3 patients and 4 upcoming weekday appointments).
- Swagger UI is available at <http://localhost:8080/swagger-ui.html>.

## Testing the main functions

Automated tests:

```powershell
.\mvnw.cmd test              # full test suite (54 tests)
.\mvnw.cmd clean verify      # clean build and tests
```

The suite covers the persistence rules (constraints and atomic slot writes), the service rules (validation, ordering and error translation), the HTTP contract (status codes and error bodies) and the concurrency behaviour (racing bookings, see `evidence/concurrency-test.txt`).

Manual tests, option 1 (browser):

1. Open <http://localhost:8080>. Register a patient and check that the table updates.
2. Register the same e-mail again. The page shows an error banner with the code `PATIENT_EXISTS`.
3. Open the booking desk (or click Book in the table). Choose a patient and book a slot (for example 2030-01-07 at 10:00 for 30 minutes).
4. Book an overlapping slot (for example at 10:15). The page shows an error banner with the code `APPOINTMENT_CONFLICT`.
5. Reschedule the first booking, then cancel it. The list updates after each action.

Manual tests, option 2 (API with `curl.exe`):

Windows PowerShell mangles JSON that is typed directly into `curl -d`, so the request bodies live in the `requests` folder and are passed as files (with `--data-binary "@file"`).

```powershell
# list the seeded patients (200)
curl.exe -s http://localhost:8080/api/v1/patients

# register a patient (201 with a Location header)
curl.exe -s -i -X POST http://localhost:8080/api/v1/patients `
    -H "Content-Type: application/json" --data-binary "@requests\valid-patient.json"

# book a slot for patient 4 (201)
curl.exe -s -i -X POST http://localhost:8080/api/v1/patients/4/appointments `
    -H "Content-Type: application/json" --data-binary "@requests\valid-booking.json"

# book an overlapping slot (409 APPOINTMENT_CONFLICT)
curl.exe -s -X POST http://localhost:8080/api/v1/patients/4/appointments `
    -H "Content-Type: application/json" --data-binary "@requests\overlapping-booking.json"

# cancel the booking (204)
curl.exe -s -i -X DELETE http://localhost:8080/api/v1/appointments/5
```

Every request, with its expected result, is listed in `requests/clinic.http` (send requests directly from VS Code with the REST Client extension). Captured output from these checks is in `evidence/api-checks.txt`, and the concurrency test output is in `evidence/concurrency-test.txt`.

## Database setup, ports and configuration

- Database: embedded H2 in memory (`jdbc:h2:mem:clinicdb`). No installation, credentials or separate process are needed.
- The schema is created from the JPA entities at startup (create-drop) and the demo data is inserted then. All data is lost at every restart (see limitations below).
- The documented schema is in `database/schema-reference.sql`.
- Port: 8080. The environment variable `SERVER_PORT` overrides it.
- Other configuration lives in `src/main/resources/application.properties` (the health endpoint is exposed at `/actuator/health`, error responses contain no stack traces).
- Booking rules are defined in the service layer: opening hours (Monday to Friday, 09:00 to 17:00) in `AppointmentService`, booking length (10 to 180 minutes) in `AppointmentRequest`.

## Project structure

```text
comp713-individual-assignment/
  src/main/java/nz/ac/aut/comp713/clinic/    Java sources (controller, service, repository, model, config)
  src/main/resources/static/                 Browser client (index.html, appointments.html, js and css)
  src/main/resources/application.properties  Configuration
  src/test/java/                             Tests (unit, web contract and concurrency)
  database/schema-reference.sql              Documented database schema
  requests/clinic.http                       All API requests with expected results
  requests/*.json                            Request bodies for curl.exe
  evidence/                                  Captured test output
  docs/architecture.md                       Architecture and communication notes
```

## Known limitations and unresolved problems

- Data is held in memory and resets at every restart (the demo data reloads). A real deployment would use file-based H2 or a database server such as MySQL.
- Times are stored as clinic-local time (`LocalDateTime`) and a single clock is assumed. There is no UTC or timezone handling.
- There is no authentication or role management. Every visitor can see and change all data.
- The no-overlap rule is per patient only. There is no room or doctor schedule, so two different patients can book the same time (this is intended for a single shared calendar).
- The patient list is not paginated (fine at the demo scale).
- Opening hours are checked in the service layer only. The database does not enforce them.

## Architecture

See `docs/architecture.md` for the layers, the communication flow, the error model and how the design maps to the COMP713 concepts.