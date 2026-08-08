import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { buildAccentPalette } from './colorUtils'

export const DEFAULT_ACCENT = '#3D6EDC'
const STORAGE_KEY = 'clinicos-theme'

const ThemeContext = createContext(null)

function getInitialTheme() {
  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark') return stored
  if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark'
  return 'light'
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(getInitialTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    const palette = buildAccentPalette(DEFAULT_ACCENT, theme)
    Object.entries(palette).forEach(([key, value]) => document.documentElement.style.setProperty(key, value))
    window.localStorage.setItem(STORAGE_KEY, theme)
  }, [theme])

  const value = useMemo(() => ({
    theme,
    toggleTheme: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')),
  }), [theme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme deve ser usado dentro de um ThemeProvider')
  return ctx
}
