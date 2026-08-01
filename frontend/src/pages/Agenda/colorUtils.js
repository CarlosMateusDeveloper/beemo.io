function hexToRgb(hex) {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
}

export function mixHexColors(colorA, colorB, t) {
  const a = hexToRgb(colorA)
  const b = hexToRgb(colorB)
  return '#' + a.map((v, i) => Math.round(v + (b[i] - v) * t).toString(16).padStart(2, '0')).join('')
}

export function buildAccentPalette(accent) {
  return {
    '--acc': accent,
    '--acc-deep': mixHexColors(accent, '#1B2B4B', 0.45),
    '--acc-soft': mixHexColors(accent, '#FFFFFF', 0.9),
    '--acc-line': mixHexColors(accent, '#FFFFFF', 0.7),
  }
}
