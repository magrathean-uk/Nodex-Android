---
version: alpha
name: Nodex-Android
description: Android monitoring UI with Material 3 structure, iOS-blue brand cues, and compact technical readability.
colors:
  background: "#0F172A"
  background-alt: "#1E293B"
  surface: "#111827"
  surface-alt: "#1F2937"
  primary: "#007AFF"
  secondary: "#0A84FF"
  tertiary: "#34C759"
  text: "#E5E7EB"
  text-muted: "#94A3B8"
  success: "#34C759"
  warning: "#FF9500"
  danger: "#FF3B30"
typography:
  display-lg:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "32px"
    fontWeight: 700
    lineHeight: "40px"
    letterSpacing: "0em"
  headline-md:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: "28px"
    letterSpacing: "0em"
  body-md:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0.03em"
  label-sm:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: "16px"
    letterSpacing: "0.04em"
  mono-sm:
    fontFamily: "Roboto Mono, ui-monospace, monospace"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "16px"
    letterSpacing: "0em"
rounded:
  sm: "12px"
  md: "14px"
  lg: "18px"
  xl: "24px"
  full: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  xxl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#041019"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
    height: "48px"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  status-chip:
    backgroundColor: "{colors.surface-alt}"
    textColor: "{colors.tertiary}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    padding: "{spacing.sm}"
  input-field:
    backgroundColor: "{colors.surface-alt}"
    textColor: "{colors.text}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
---

## Overview
Nodex-Android should feel native to Material 3 while keeping the portfolio's blue monitoring identity. It is direct, practical, and built for quick server inspection on a phone.

## Colors
Blue drives actions and navigation emphasis. Green, amber, and red mark server state. Dark neutrals should support the default look even though dynamic color may adapt the theme on newer devices.

## Typography
Typography is simple and functional. Body copy stays at readable Material sizing, while labels and status chips remain short and dense.

## Layout
Use standard Android cards, stacked sections, and strong vertical flow. Inputs and quick actions should stay close to the server record or system area they affect.

## Elevation & Depth
Prefer Material tonal layering over heavy shadow. Depth should come from card contrast and section grouping, not decorative chrome.

## Shapes
Use medium-rounded cards and controls. Compact state chips should be fully rounded. Avoid mixing sharp and soft geometry on the same screen.

## Components
Server cards, alert chips, form fields, and action buttons are the main building blocks. Technical detail can be dense, but the outer shell should remain clear and touch-friendly.

## Do's and Don'ts
- Do preserve a strong blue monitoring identity even with dynamic color support.
- Do keep health colors reserved for real state.
- Don't over-style utility surfaces with decorative gradients.
- Don't hide warnings inside low-emphasis cards.
