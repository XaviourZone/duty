# Automated Duty Roster Management Web Application

⚓ **Live Application:** [https://xaviourzone.github.io/duty/](https://xaviourzone.github.io/duty/)

An automated naval officer duty roster generator and management system designed for watchkeeping operations.

## Key Features
- **Two Independent Routines**:
  - **Normal Routine**: Monday to Friday (excluding holidays).
  - **Sunday Routine**: Saturdays, Sundays, and declared holidays.
- **Cross-Routine Calendar Rest Rule**:
  - No consecutive calendar day duties (e.g., Friday duty cannot be assigned on Saturday).
  - An officer on Friday can be assigned on Sunday (with Saturday as rest).
- **Leave Management**: Excludes officers on leave, prefix holidays, and suffix holidays. Monday leave restricts preceding Sunday. Returning officers receive priority on their return date.
- **Fair Duty Distribution**: Tenure-based pacing ensures new joiners are not overloaded.
- **Duty Swapping**: Validates mutual exchanges against rest restrictions and displays `Officer B (swapped with Officer A)`.
- **Excel Import / Export**: Standard Excel workbook import and export without resetting historical counts.
- **Watchkeeper Portal**: Dedicated management workspace with default password `admin@123`.
- **Executive Styling**: Dual Light and Dark Sapphire modes with Google Plus Jakarta Sans typography and micro-animations.

## Running Locally
Double-click `index.html` or run with any static server:
```bash
npx serve .
```
Or start the Node.js backend:
```bash
npm install
node server.js
```
