1. **Write failing tests** based on the business scenarios below.
2. **Make the tests pass** by implementing the logic.
3. **Refactor** for clarity and readability (optional rounding, input checks).
4. **Make sure tests are still green** and cover all relevant paths.

## Goal
Implement a function that calculates how long a starship needs to travel a given distance based on its MGLT (MegaLights per hour), using **Test-Driven Development**.

---

## Business Context
You’re building a planning tool for the Rebel Alliance logistics system. Coordinators need to estimate travel time across the galaxy using data from SWAPI.

Your job is to implement this logic safely and test it rigorously – so it works both in daily operations and in extreme edge cases.

---

## Specification

```
function calculateTravelTime(starship: Starship, distanceInMglt: number): number
```

- `starship` is an object from SWAPI (mocked in tests).
- `distanceInMglt` is the route’s distance, in MGLT units.
- The function returns the time in **hours**.

---

As a rebel 
I want to calculate how long starship needs to travel from the given distance
to plan the travel
