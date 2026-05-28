export function fallback<T>(promise: Promise<T>, defaultValue: T): Promise<T> {
  return promise.catch((err) => {
    console.warn('fetchOptions partial fail:', err)
    return defaultValue
  })
}
