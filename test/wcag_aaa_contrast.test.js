/**
 * LanceDrop — WCAG AAA Automated Contrast Verification Suite
 *
 * Implements strict mathematical verification of relative luminance and contrast
 * ratios according to the official W3C WCAG 2.1 / 2.2 specifications:
 * - Relative luminance: L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin
 * - Contrast ratio: (L1 + 0.05) / (L2 + 0.05) where L1 >= L2
 *
 * Standard thresholds:
 * - WCAG AAA Normal Text (< 18pt regular, < 14pt bold): Contrast ratio >= 7.0:1
 * - WCAG AAA Large Text (>= 18pt regular, >= 14pt bold): Contrast ratio >= 4.5:1
 * - WCAG 2.1 Non-text UI Components & Graphical Objects: Contrast ratio >= 3.0:1
 */

import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

// ---------------------------------------------------------------------------
// 1. WCAG 2.1 / 2.2 Mathematical Primitives
// ---------------------------------------------------------------------------

/**
 * Parses a 3-digit or 6-digit hexadecimal color string into RGB integers (0-255).
 * @param {string} hex - e.g. '#FFFFFF', '#0B1220', '00D2FF', '#FFF'
 * @returns {{ r: number, g: number, b: number }}
 */
export function parseColor(hex) {
  if (typeof hex !== 'string') {
    throw new TypeError(`Expected hex string, received: ${typeof hex}`);
  }
  const clean = hex.trim().replace(/^#/, '');

  if (clean.length === 3) {
    const r = parseInt(clean[0] + clean[0], 16);
    const g = parseInt(clean[1] + clean[1], 16);
    const b = parseInt(clean[2] + clean[2], 16);
    if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) {
      throw new Error(`Invalid 3-digit hex color: ${hex}`);
    }
    return { r, g, b };
  }

  if (clean.length === 6) {
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) {
      throw new Error(`Invalid 6-digit hex color: ${hex}`);
    }
    return { r, g, b };
  }

  throw new Error(`Unsupported hex color length (${clean.length}): ${hex}`);
}

/**
 * Computes the relative luminance of a color according to the W3C WCAG formula:
 * L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin
 * where C_lin = (C_sRGB <= 0.04045) ? C_sRGB / 12.92 : ((C_sRGB + 0.055) / 1.055) ^ 2.4
 * @param {string} hex - Hexadecimal color string
 * @returns {number} Relative luminance value in range [0.0, 1.0]
 */
export function getRelativeLuminance(hex) {
  const { r, g, b } = parseColor(hex);
  const channels = [r / 255, g / 255, b / 255].map((val) => {
    return val <= 0.04045
      ? val / 12.92
      : Math.pow((val + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

/**
 * Computes the WCAG contrast ratio between two colors:
 * CR = (L1 + 0.05) / (L2 + 0.05), where L1 is the lighter color and L2 is the darker color.
 * @param {string} hex1 - First color
 * @param {string} hex2 - Second color
 * @returns {number} Contrast ratio in range [1.0, 21.0]
 */
export function getContrastRatio(hex1, hex2) {
  const lum1 = getRelativeLuminance(hex1);
  const lum2 = getRelativeLuminance(hex2);
  const l1 = Math.max(lum1, lum2);
  const l2 = Math.min(lum1, lum2);
  return (l1 + 0.05) / (l2 + 0.05);
}

// ---------------------------------------------------------------------------
// 2. Authoritative LanceDrop Semantic Color Tokens (Light & Dark)
// ---------------------------------------------------------------------------

export const DESIGN_SYSTEM_TOKENS = {
  light: {
    surfaces: {
      canvas: { name: 'Canvas Background', hex: '#F0F4F9', cssVar: '--bg-app' },
      card: { name: 'Card / Surface', hex: '#FFFFFF', cssVar: '--bg-card' },
      elevated: { name: 'Elevated Surface', hex: '#F1F5F9', cssVar: '--bg-card-hover' }
    },
    normalText: {
      primary: { name: 'Primary Text', hex: '#0B1220', cssVar: '--text-main' },
      secondary: { name: 'Secondary Text', hex: '#334155', cssVar: '--text-secondary' },
      muted: { name: 'Muted Text', hex: '#384656', cssVar: '--text-muted' },
      brandLink: { name: 'Brand Text Link', hex: '#0B4CA5', cssVar: '--blue-link' },
      successText: { name: 'Success Status Text', hex: '#14532D', cssVar: '--green-success-text' },
      errorText: { name: 'Error Status Text', hex: '#991B1B', cssVar: '--red-danger-text' },
      warningText: { name: 'Warning Status Text', hex: '#78350F', cssVar: '--orange-warn-text' }
    },
    uiComponents: {
      borderInteractive: { name: 'Interactive Border / Input', hex: '#64748B', cssVar: '--border-input' },
      successIcon: { name: 'Success Status Icon / UI', hex: '#15803D', cssVar: '--green-success' },
      errorIcon: { name: 'Error Status Icon / UI', hex: '#DC2626', cssVar: '--red-danger' },
      warningIcon: { name: 'Warning Status Icon / UI', hex: '#B45309', cssVar: '--orange-warn' },
      focusRing: { name: 'Focus Indicator Ring', hex: '#0B4CA5', cssVar: '--focus-ring' }
    },
    buttonPrimary: {
      background: { name: 'Brand Primary Action', hex: '#0B4CA5', cssVar: '--blue-primary' },
      onPrimary: { name: 'On-Primary Button Label', hex: '#FFFFFF', cssVar: '--blue-on-primary' }
    }
  },
  dark: {
    surfaces: {
      canvas: { name: 'Canvas Background', hex: '#0B0F17', cssVar: '--bg-app' },
      card: { name: 'Card / Surface', hex: '#161F30', cssVar: '--bg-card' },
      elevated: { name: 'Elevated Surface', hex: '#1E293B', cssVar: '--bg-card-hover' }
    },
    normalText: {
      primary: { name: 'Primary Text', hex: '#F8FAFC', cssVar: '--text-main' },
      secondary: { name: 'Secondary Text', hex: '#E2E8F0', cssVar: '--text-secondary' },
      muted: { name: 'Muted Text', hex: '#CBD5E1', cssVar: '--text-muted' },
      brandLink: { name: 'Brand Text Link', hex: '#7DD3FC', cssVar: '--blue-link' },
      successText: { name: 'Success Status Text', hex: '#86EFAC', cssVar: '--green-success-text' },
      errorText: { name: 'Error Status Text', hex: '#FECDD3', cssVar: '--red-danger-text' },
      warningText: { name: 'Warning Status Text', hex: '#FDE68A', cssVar: '--orange-warn-text' }
    },
    uiComponents: {
      borderInteractive: { name: 'Interactive Border / Input', hex: '#64748B', cssVar: '--border-input' },
      successIcon: { name: 'Success Status Icon / UI', hex: '#10B981', cssVar: '--green-success' },
      errorIcon: { name: 'Error Status Icon / UI', hex: '#F43F5E', cssVar: '--red-danger' },
      warningIcon: { name: 'Warning Status Icon / UI', hex: '#F59E0B', cssVar: '--orange-warn' },
      focusRing: { name: 'Focus Indicator Ring', hex: '#00D2FF', cssVar: '--focus-ring' }
    },
    buttonPrimary: {
      background: { name: 'Brand Primary Action', hex: '#00D2FF', cssVar: '--blue-primary' },
      onPrimary: { name: 'On-Primary Button Label', hex: '#070C14', cssVar: '--blue-on-primary' }
    }
  }
};

// ---------------------------------------------------------------------------
// 3. Automated Test Suite
// ---------------------------------------------------------------------------

describe('WCAG AAA Design System: Mathematical Contrast & Luminance Engine', () => {
  test('parseColor correctly parses 3-digit and 6-digit hex representations', () => {
    assert.deepEqual(parseColor('#000000'), { r: 0, g: 0, b: 0 });
    assert.deepEqual(parseColor('#ffffff'), { r: 255, g: 255, b: 255 });
    assert.deepEqual(parseColor('FFF'), { r: 255, g: 255, b: 255 });
    assert.deepEqual(parseColor('#0B1220'), { r: 11, g: 18, b: 32 });
    assert.deepEqual(parseColor('00D2FF'), { r: 0, g: 210, b: 255 });

    assert.throws(() => parseColor(12345), TypeError);
    assert.throws(() => parseColor('#GGGGGG'), Error);
    assert.throws(() => parseColor('#1234'), Error);
  });

  test('getRelativeLuminance matches official W3C reference boundaries', () => {
    // Pure Black = 0.0
    const blackLum = getRelativeLuminance('#000000');
    assert.strictEqual(blackLum, 0.0);

    // Pure White = 1.0
    const whiteLum = getRelativeLuminance('#FFFFFF');
    assert.strictEqual(Math.round(whiteLum * 1000) / 1000, 1.0);

    // Pure sRGB primaries
    const redLum = getRelativeLuminance('#FF0000');
    assert.strictEqual(Math.round(redLum * 10000) / 10000, 0.2126);

    const greenLum = getRelativeLuminance('#00FF00');
    assert.strictEqual(Math.round(greenLum * 10000) / 10000, 0.7152);

    const blueLum = getRelativeLuminance('#0000FF');
    assert.strictEqual(Math.round(blueLum * 10000) / 10000, 0.0722);
  });

  test('getContrastRatio satisfies mathematical properties (range 1:1 to 21:1, commutative)', () => {
    // Contrast of identical colors is exactly 1.0:1
    assert.strictEqual(getContrastRatio('#FFFFFF', '#FFFFFF'), 1.0);
    assert.strictEqual(getContrastRatio('#161F30', '#161F30'), 1.0);

    // Contrast of Black vs White is exactly 21.0:1
    const maxRatio = getContrastRatio('#000000', '#FFFFFF');
    assert.strictEqual(Math.round(maxRatio * 10) / 10, 21.0);

    // Commutativity: CR(c1, c2) === CR(c2, c1)
    const ratioA = getContrastRatio('#0B4CA5', '#FFFFFF');
    const ratioB = getContrastRatio('#FFFFFF', '#0B4CA5');
    assert.strictEqual(ratioA, ratioB);
  });

  test('Detection test: obsolete non-AAA colors fail strict AAA threshold (>= 7.0:1)', () => {
    // Legacy developer palette: #64748B text on #FFFFFF card achieves only ~4.76:1 (fails AAA)
    const legacyMutedRatio = getContrastRatio('#64748B', '#FFFFFF');
    assert.ok(legacyMutedRatio < 7.0, `Legacy muted #64748B should fail AAA (got ${legacyMutedRatio.toFixed(2)})`);

    // Legacy blue: #1A73E8 on #FFFFFF achieves only ~4.51:1 (fails AAA)
    const legacyBlueRatio = getContrastRatio('#1A73E8', '#FFFFFF');
    assert.ok(legacyBlueRatio < 7.0, `Legacy blue #1A73E8 should fail AAA (got ${legacyBlueRatio.toFixed(2)})`);
  });
});

describe('WCAG AAA Design System: Light Mode Semantic Tokens Verification', () => {
  const { surfaces, normalText, uiComponents, buttonPrimary } = DESIGN_SYSTEM_TOKENS.light;

  for (const [sKey, surface] of Object.entries(surfaces)) {
    test(`Light Mode: Normal text tokens achieve >= 7.0:1 on ${surface.name} (${surface.hex})`, () => {
      for (const [tKey, token] of Object.entries(normalText)) {
        const ratio = getContrastRatio(token.hex, surface.hex);
        assert.ok(
          ratio >= 7.0,
          `Light Mode FAIL: ${token.name} (${token.hex} / ${token.cssVar}) on ${surface.name} (${surface.hex}) has contrast ${ratio.toFixed(2)}:1 (required >= 7.0:1)`
        );
      }
    });

    test(`Light Mode: Non-text UI components and icons achieve >= 3.0:1 on ${surface.name} (${surface.hex})`, () => {
      for (const [uKey, component] of Object.entries(uiComponents)) {
        const ratio = getContrastRatio(component.hex, surface.hex);
        assert.ok(
          ratio >= 3.0,
          `Light Mode FAIL: ${component.name} (${component.hex} / ${component.cssVar}) on ${surface.name} (${surface.hex}) has contrast ${ratio.toFixed(2)}:1 (required >= 3.0:1)`
        );
      }
    });
  }

  test('Light Mode: Primary Action button label achieves >= 7.0:1 on button background', () => {
    const ratio = getContrastRatio(buttonPrimary.onPrimary.hex, buttonPrimary.background.hex);
    assert.ok(
      ratio >= 7.0,
      `Light Mode Button FAIL: ${buttonPrimary.onPrimary.name} (${buttonPrimary.onPrimary.hex}) on ${buttonPrimary.background.name} (${buttonPrimary.background.hex}) has contrast ${ratio.toFixed(2)}:1 (required >= 7.0:1)`
    );
  });
});

describe('WCAG AAA Design System: Dark Mode Semantic Tokens Verification', () => {
  const { surfaces, normalText, uiComponents, buttonPrimary } = DESIGN_SYSTEM_TOKENS.dark;

  for (const [sKey, surface] of Object.entries(surfaces)) {
    test(`Dark Mode: Normal text tokens achieve >= 7.0:1 on ${surface.name} (${surface.hex})`, () => {
      for (const [tKey, token] of Object.entries(normalText)) {
        const ratio = getContrastRatio(token.hex, surface.hex);
        assert.ok(
          ratio >= 7.0,
          `Dark Mode FAIL: ${token.name} (${token.hex} / ${token.cssVar}) on ${surface.name} (${surface.hex}) has contrast ${ratio.toFixed(2)}:1 (required >= 7.0:1)`
        );
      }
    });

    test(`Dark Mode: Non-text UI components and icons achieve >= 3.0:1 on ${surface.name} (${surface.hex})`, () => {
      for (const [uKey, component] of Object.entries(uiComponents)) {
        const ratio = getContrastRatio(component.hex, surface.hex);
        assert.ok(
          ratio >= 3.0,
          `Dark Mode FAIL: ${component.name} (${component.hex} / ${component.cssVar}) on ${surface.name} (${surface.hex}) has contrast ${ratio.toFixed(2)}:1 (required >= 3.0:1)`
        );
      }
    });
  }

  test('Dark Mode: Primary Action button label achieves >= 7.0:1 on button background', () => {
    const ratio = getContrastRatio(buttonPrimary.onPrimary.hex, buttonPrimary.background.hex);
    assert.ok(
      ratio >= 7.0,
      `Dark Mode Button FAIL: ${buttonPrimary.onPrimary.name} (${buttonPrimary.onPrimary.hex}) on ${buttonPrimary.background.name} (${buttonPrimary.background.hex}) has contrast ${ratio.toFixed(2)}:1 (required >= 7.0:1)`
    );
  });
});
