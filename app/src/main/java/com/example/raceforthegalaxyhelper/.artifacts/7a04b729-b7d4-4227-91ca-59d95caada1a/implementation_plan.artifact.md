# 🚀 Feature Plan: Refactor Select Action UI (GameScreen)

**Target:** Refactor the "Select Action" UI within [MainActivity.kt](file:///H:/Android/RaceForTheGalaxyHelper/app/src/main/java/com/example/MainActivity.kt) to transition from a simple text list to a rich, card-based grid interface, aligning with the detailed mockup.

**Goal:** To enhance player engagement and visual clarity by presenting game actions as interactive, visually distinct cards that clearly communicate costs, bonuses, and current selection status, while strictly maintaining the existing game logic and data model.

---

### 🎯 Goal Description

The current action selection interface is a simple, text-heavy list. This refactor aims to transform this into a modern, highly visual grid of action cards.

The new design must achieve the following:
1.  **Card Layout:** Implement a responsive, single-column card grid optimized for phone view. Each action is a distinct card.
2.  **Visual Hierarchy:** The card must clearly separate the primary action description from secondary details (Costs/Bonuses) using typography and layout.
3.  **Interaction:** Introduce clear selection affordances (e.g., a visible subtle background change on the badge) that drive the underlying game state logic, without altering the state management.
4.  **Reveal States:** Implement logic for when the action is revealed to the players:
    *   **Current Player:** Their chosen action/bonus must be prominently highlighted/framed using a custom border colored according to their player's color.
    *   **Other Players:** Their choices must be displayed in a dedicated column or area to show who chose what. For choices made by multiple players, the border must be a dashed line alternating the selected players' colors.
5.  **View Toggle:** Implement a UI toggle button to allow users to switch between the existing Text Mode and the new Advanced Card View.

### 🧐 User Review Required

No further user review is required at this stage. The feedback provided confirms the acceptance of the plan and the current code's functional integrity.

### ❓ Open Questions

All previously identified open questions are now resolved by the user's feedback:
1.  **State Management Location:** Existing state management in `GameScreen` remains untouched.
2.  **Symbol/Cost Data Type:** The current string-based data model is sufficient.
3.  **Card Size/Adaptivity:** The view is strictly limited to a single-column grid optimized for phone screens.

### 🛠️ Proposed Changes

#### A. Data Model Updates
**NO CHANGE:** The existing data model (`ActionItem` and `AppStrings`) will be maintained to preserve current game behavior.

#### B. UI/Rendering Logic
**File:** [MainActivity.kt](file:///H:/Android/RaceForTheGalaxyHelper/app/src/main/java/com/example/MainActivity.kt)

1.  **New Toggle State:** Introduce a state variable (`isCardViewEnabled: Boolean`) in `GameScreen` to control the display mode.
2.  **Primary Composable:** The `LazyVerticalGrid` will be conditionally rendered:
    *   If `isCardViewEnabled` is false: Render the existing text-based action list.
    *   If `isCardViewEnabled` is true: Render a custom `ActionCard` for each action.
3.  **ActionCard Composable:** A new, complex composable will be created to display the action name, description, and symbol/bonus text in a structured, card-like format.
4.  **Reveal Border Logic:** The `ActionCard` will contain logic to calculate and apply a custom `Modifier.border` or `Modifier.drawWithContent` based on:
    *   The player's color (`playerBgIndices`).
    *   The set of player indices who selected this action (`selectorPlayerIndices`).
    *   If `selectorPlayerIndices.size > 1`, a dashed border will be drawn, alternating colors based on the index sequence.
5.  **Current Player Highlight:** A specific border/color will be applied to the current player's selected action, confirming their choice.
6.  **Toggle Implementation:** Implement a simple `Switch` or `Button` UI element at the top of the `GameScreen` to toggle `isCardViewEnabled`.

### ✅ Verification Plan

1.  **Visual Rendering:** Use `render_compose_preview` on the new `ActionCard` component (both Text and Card mode) to confirm visual fidelity against the mockup.
2.  **Interaction Verification:** Unit and UI tests to ensure toggling the view mode works and that selecting an action updates the state correctly, regardless of the view type.
3.  **Reveal Logic Verification:** UI tests to verify the complex dashed border coloring for multiple selections during the reveal phase.

---
*This plan is approved and ready for execution.*