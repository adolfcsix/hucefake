const icons = {
  home:'m3 10 9-7 9 7v10h-6v-6H9v6H3z',
  calendar:'M4 7h16v13H4z M4 11h16 M8 4v5m8-5v5 M8 15h2m4 0h2',
  layers:'m3 8 9-5 9 5-9 5z M3 12l9 5 9-5M3 16l9 5 9-5',
  grid:'M4 4h16v16H4zM4 9h16M9 9v11m6-11v11',
  spark:'M12 2 9 9 2 12l7 3 3 7 3-7 7-3-7-3z',
  check:'M5 3h14v18H5z m3 9 3 3 5-6',
  folder:'M3 6h7l2 3h9v11H3z',
  clock:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 4v5l3 2',
  repeat:'M4 8h15m-4-4 4 4-4 4m5 4H5m4-4-4 4 4 4',
  chart:'M4 3v17h17 M8 16v-4m4 4V6m4 10v-7m4 7V5',
  bell:'M5 17h14l-2-3V9a5 5 0 0 0-10 0v5zm5 3h4',
  exit:'M11 5H5v14h6m2-7h8m-4-4 4 4-4 4',
  menu:'M4 6h16M4 12h16M4 18h16',
  close:'M6 6l12 12M18 6 6 18',
  collapse:'M4 4h16v16H4z M10 4v16 m6-11-3 3 3 3',
  expand:'M4 4h16v16H4z M10 4v16 m3-11 3 3-3 3',
  arrow:'M5 12h14m-6-6 6 6-6 6',
  user:'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-8 8v-2a8 8 0 0 1 16 0v2',
  book:'M12 6c-3-2-6-2-9-1v14c3-1 6-1 9 1 3-2 6-2 9-1V5c-3-1-6-1-9 1Zm0 0v14'
}
export function Icon({ name }) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={icons[name]} /></svg>
}
