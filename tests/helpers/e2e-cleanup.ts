import type { APIRequestContext } from '@playwright/test'

type Headers = Record<string, string>

function uniqueIds(ids: Array<number | undefined>) {
  return [...new Set(ids.filter((id): id is number => typeof id === 'number'))].reverse()
}

export async function deleteResourceIds(
  request: APIRequestContext,
  apiUrl: string,
  headers: Headers,
  resourcePath: string,
  ids: Array<number | undefined>,
) {
  for (const id of uniqueIds(ids)) {
    await request.delete(`${apiUrl}${resourcePath}/${id}`, { headers }).catch(() => {})
  }
}

export async function deleteSchedulesForClass(
  request: APIRequestContext,
  apiUrl: string,
  headers: Headers,
  classId: number | undefined,
) {
  if (!classId) {
    return
  }
  const response = await request.get(`${apiUrl}/api/schedules/class/${classId}`, { headers }).catch(() => null)
  if (!response) {
    return
  }
  const body = await response.json().catch(() => null)
  const records = Array.isArray(body?.data) ? body.data : []
  await deleteResourceIds(
    request,
    apiUrl,
    headers,
    '/api/schedules',
    records.map((record: any) => record?.id),
  )
}
