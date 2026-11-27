import { useEffect, useState } from 'react'

export function useFetch(loader, deps) {
  const [state, setState] = useState({ data: null, error: null })
  useEffect(() => {
    let current = true
    async function load() {
      try {
        const data = await loader()
        if (current) setState({ data, error: null })
      } catch (error) {
        if (current) setState({ data: null, error })
      }
    }
    load()
    return () => { current = false }
  }, deps)
  return state
}
