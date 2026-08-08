function hexToRgb(hex) {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
}

export function mixHexColors(colorA, colorB, t) {
  const a = hexToRgb(colorA)
  const b = hexToRgb(colorB)
  return '#' + a.map((v, i) => Math.round(v + (b[i] - v) * t).toString(16).padStart(2, '0')).join('')
}

const DARK_SURFACE = '#1A1E27'

/**
 * Deriva as variantes do accent a partir de uma única cor base. Em dark
 * mode a mistura muda de alvo: "soft"/"line" tendem para a superfície
 * escura (em vez de branco) e "deep" clareia (em vez de escurecer),
 * porque é usada como cor de texto sobre o fundo "soft".
 */
export function buildAccentPalette(accent, theme = 'light') {
  if (theme === 'dark') {
    return {
      '--acc': accent,
      '--acc-deep': mixHexColors(accent, '#FFFFFF', 0.55),
      '--acc-soft': mixHexColors(accent, DARK_SURFACE, 0.75),
      '--acc-line': mixHexColors(accent, DARK_SURFACE, 0.45),
    }
  }
  return {
    '--acc': accent,
    '--acc-deep': mixHexColors(accent, '#1B2B4B', 0.45),
    '--acc-soft': mixHexColors(accent, '#FFFFFF', 0.9),
    '--acc-line': mixHexColors(accent, '#FFFFFF', 0.7),
  }
}
