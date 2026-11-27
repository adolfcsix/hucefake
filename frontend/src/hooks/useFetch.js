import { useEffect, useState } from 'react'

export function useFetch(loader, deps) {
  const [state, setState] = useState({ data: null, error: null })
  useEffect(() => {
    let current = true
    loader().then(data => { if (current) setState({ data, error: null }) })
      .catch(error => { if (current) setState({ data: null, error }) })
    return () => { current = false }
  }, deps)
  return state
}
