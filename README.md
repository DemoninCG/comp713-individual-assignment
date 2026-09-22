# Clinic Appointment System

COMP713 Assignment 2 (Option A — Distributed Web/API Application).

A small distributed web/API application: simple HTML/JS client pages in the browser communicate
with a server-side REST API, which manages clinic data (patients and their appointments) in a
relational database.

> **Note:** this README grows with the project (see the commit history). The full setup, testing
> and limitation notes are completed at the end of development.

## Requirements

- JDK 21 (Eclipse Temurin / OpenJDK)
- A web browser and `curl.exe` for manual testing
- No separate Maven install needed — the Maven Wrapper (`mvnw.cmd`) is included

## Run

```powershell
cd comp713-individual-assignment
.\mvnw.cmd spring-boot:run
```

The service listens on <http://localhost:8080> (override with the `SERVER_PORT` environment variable).

## Test

```powershell
.\mvnw.cmd test
curl.exe http://localhost:8080/actuator/health
```

## Current status (Stage 1: skeleton)

- Spring Boot 3.5.16 / Java 21 project with Maven Wrapper
- Health endpoint via Spring Boot Actuator (`GET /actuator/health`)
- Planned: patient + appointment REST API (CRUD + booking workflow), two client pages,
  embedded H2 persistence, input validation and structured error handling