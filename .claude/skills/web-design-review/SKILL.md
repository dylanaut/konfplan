---
name: web-design-review
description: Review and improve the UI/UX of a web application against established, framework-agnostic design and usability standards (Nielsen's heuristics, WCAG accessibility, responsive design, visual consistency). Use this skill whenever the user asks for a "design check", "UI review", "UX audit", or feedback on layout, accessibility, consistency, or usability of a browser-based web app (Vue/React/Tailwind/plain HTML/etc.). Not for native desktop app design (traffic lights, window chrome, Electron/Tauri) - use apple-design for that instead.
---

# Web App Design Review Skill

Review interfaces the way real users experience them: can they find what they need, understand
what just happened, recover from a mistake, and get through the app without a mouse if they have
to. A good web app doesn't need explaining - the UI itself teaches the user how it works.

## Core Philosophy

Every screen answers three questions at a glance: **Where am I? What can I do here? What just
happened?** If a user has to guess, hesitate, or re-read, that's a finding. Consistency beats
cleverness - the same action should look and behave the same way everywhere in the app.

## Quick-Start Checklist

Use this as the review structure. Go section by section against the actual running app (or
component code if the app can't be run), not just the design intent.

### 1. Nielsen's 10 Usability Heuristics
1. **Visibility of system status** - loading spinners, save confirmations, progress indicators for anything taking >1s
2. **Match between system and the real world** - labels/icons/terminology match the user's domain language, not internal data-model names
3. **User control and freedom** - obvious way to cancel/undo/go back out of any flow, especially destructive actions
4. **Consistency and standards** - same component (button, modal, table) looks/behaves identically everywhere; platform conventions respected (e.g. primary action on the right in dialogs)
5. **Error prevention** - confirmation before destructive/irreversible actions, disabled states instead of allowing invalid submissions
6. **Recognition rather than recall** - options/actions visible in context, not hidden behind memorized shortcuts or steps
7. **Flexibility and efficiency of use** - power-user paths (bulk actions, filters, sort) don't get in a beginner's way
8. **Aesthetic and minimalist design** - no unused/duplicate/dead UI; every element earns its place
9. **Help users recognize, diagnose, and recover from errors** - error messages state what went wrong AND what to do next, in plain language, near the field/action that caused it
10. **Help and documentation** - findable when needed, but the UI should need it as little as possible

### 2. Accessibility (WCAG 2.1 AA baseline)
- Color contrast: body text ≥4.5:1, large text/icons ≥3:1 against its background
- Every interactive element reachable and operable via keyboard alone (Tab order matches visual order, no keyboard traps)
- Visible focus indicator on every focusable element (never `outline: none` without a replacement)
- Form inputs have associated `<label>`s (not just placeholder text, which disappears on input)
- Images/icons conveying meaning have `alt`/`aria-label`; purely decorative ones are hidden from assistive tech
- Color is never the *only* signal (e.g. red text alone for errors - pair with an icon/label)
- Interactive touch targets ≥24x24px (WCAG) / 44x44px (comfortable) with adequate spacing

### 3. Visual & Spacing Consistency
- Spacing follows a defined scale (e.g. Tailwind's `4px` steps) - flag ad-hoc pixel values in inline styles
- Color usage comes from the theme/token palette, not one-off hex codes
- Typography: a small, deliberate set of sizes/weights, not an accumulation of one-off `text-*` combinations
- Border-radius, shadow depth, and icon sizing consistent across similar components (all cards match, all buttons match)

### 4. Responsive Behavior
- Test at common breakpoints (mobile ~375px, tablet ~768px, desktop ~1280px+) - no horizontal scroll, no clipped/overlapping content
- Touch-friendly on mobile: tap targets large enough, no hover-only affordances (tooltips, hover menus) as the *only* way to reach something
- Tables/dense data views degrade sensibly on narrow screens (stacked cards, horizontal scroll with a visible cue, or column priority)

### 5. Feedback & States
Every data-driven view needs all four states designed, not just the happy path:
- **Loading** - skeleton/spinner, not a blank screen
- **Empty** - explains why it's empty and what to do next (not just "No data")
- **Error** - what went wrong + a retry/next action, not a raw stack trace or generic "Something went wrong"
- **Success** - confirms the action worked (toast, inline checkmark, updated UI) - silence after a user action reads as "did that even work?"

### 6. Forms & Validation
- Validate inline, near the field, at the right moment (on blur/submit, not on every keystroke for fields like email)
- Error messages are specific ("E-Mail-Adresse fehlt @-Zeichen", not "Ungültige Eingabe")
- Required fields marked consistently; don't rely on color alone
- Submit button disabled state clearly distinguishable from enabled (not just slightly lighter)
- Destructive submits (delete, irreversible change) require a confirmation step

### 7. Navigation & Information Architecture
- Current location always visible (active nav state, breadcrumb, page title)
- Primary actions are visually primary (one clear default action per view, not five equally-weighted buttons)
- Related actions grouped together; unrelated actions have visual separation
- Back/cancel always available and predictable - never a dead end

### 8. Performance Perception
- Optimistic UI updates for fast actions (toggle, like, reorder) rather than waiting on a round-trip
- Skeleton screens for anything with a data fetch, sized to match the eventual content (avoid layout shift)
- Debounce/throttle expensive interactions (search-as-you-type, resize handlers)

## How to Run a Review

1. Identify the actual screens/flows in scope - list them explicitly rather than reviewing in the abstract.
2. Walk each screen against the checklist above, noting concrete findings with a location (component/file or screen name) and a specific reproduction ("click X with an empty list → no empty state shown").
3. Prefer running the app and interacting with it over reading component code alone - static review misses missing loading/error states and real contrast/spacing issues.
4. Severity-rank findings: **Blocker** (breaks a task, inaccessible to keyboard/screen-reader users), **Should-fix** (inconsistent, confusing, but workable), **Polish** (nice-to-have).
5. Report findings grouped by checklist section, not as an undifferentiated list - makes it easy for the user to triage.
