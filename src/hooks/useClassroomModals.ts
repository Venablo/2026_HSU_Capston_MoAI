// Thin re-export — modal state now lives in ClassroomModalContext.
// Import useClassroomModal directly from context for new code.
export { useClassroomModal as useClassroomModals } from '../context/ClassroomModalContext'
export type { ModalKey } from '../context/ClassroomModalContext'
