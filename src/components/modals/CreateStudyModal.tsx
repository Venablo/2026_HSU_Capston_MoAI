import { useState } from 'react'
import { X, Plus } from 'lucide-react'
import { STUDY_TAGS, STUDY_MODES, PRIVACY_OPTIONS, MEMBER_LIMITS } from '../../constants'
import './CreateStudyModal.css'

interface CreateStudyModalProps {
    isOpen: boolean
    onClose: () => void
}

export interface StudyFormData {
    studyName: string
    description: string
    tags: string[]
    maxMembers: number
    isPrivate: boolean
    studyMode: 'entry' | 'ai' | ''
}

export default function CreateStudyModal({ isOpen, onClose }: CreateStudyModalProps) {
    const [formData, setFormData] = useState<StudyFormData>({
        studyName: '',
        description: '',
        tags: [],
        maxMembers: MEMBER_LIMITS.DEFAULT,
        isPrivate: false,
        studyMode: ''
    })

    const handleTagToggle = (tag: string) => {
        setFormData(prev => ({
            ...prev,
            tags: prev.tags.includes(tag)
                ? prev.tags.filter(t => t !== tag)
                : [...prev.tags, tag]
        }))
    }

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault()

        handleClose()
    }

    const handleClose = () => {
        // 폼 초기화
        setFormData({
            studyName: '',
            description: '',
            tags: [],
            maxMembers: MEMBER_LIMITS.DEFAULT,
            isPrivate: false,
            studyMode: ''
        })
        onClose()
    }

    if (!isOpen) return null

    return (
        <div className="modal-overlay" onClick={handleClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                {/* Header */}
                <div className="modal-header">
                    <h2>새 스터디 그룹 만들기</h2>
                    <button className="close-btn" onClick={handleClose}>
                        <X size={24} />
                    </button>
                </div>

                {/* Form */}
                <form onSubmit={handleSubmit}>
                    {/* 그룹 이름 */}
                    <div className="form-group">
                        <label htmlFor="studyName">
                            그룹 이름 <span className="required">*</span>
                        </label>
                        <input
                            id="studyName"
                            type="text"
                            placeholder="예: 정보처리기사 실기 준비반"
                            value={formData.studyName}
                            onChange={(e) => setFormData({ ...formData, studyName: e.target.value })}
                            maxLength={100}
                        />
                    </div>

                    {/* 그룹 설명 */}
                    <div className="form-group">
                        <label htmlFor="description">
                            그룹 설명 <span className="required">*</span>
                        </label>
                        <textarea
                            id="description"
                            placeholder="스터디 목표, 진행 방식 등을 자유롭게 작성해주세요"
                            value={formData.description}
                            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                            rows={4}
                        />
                    </div>

                    {/* 관심 태그 */}
                    <div className="form-group">
                        <label>관심 태그</label>
                        <div className="tag-selection">
                            {STUDY_TAGS.map(tag => (
                                <button
                                    key={tag}
                                    type="button"
                                    className={`tag-btn ${formData.tags.includes(tag) ? 'selected' : ''}`}
                                    onClick={() => handleTagToggle(tag)}
                                >
                                    {formData.tags.includes(tag) && <Plus size={14} className="check-icon" />}
                                    {tag}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* 최대 인원 */}
                    <div className="form-group">
                        <label htmlFor="maxMembers">
                            최대 인원
                            <span className="member-count">{formData.maxMembers}명 (최대 {MEMBER_LIMITS.MAX}명까지 설정 가능)</span>
                        </label>
                        <input
                            id="maxMembers"
                            type="range"
                            min={MEMBER_LIMITS.MIN}
                            max={MEMBER_LIMITS.MAX}
                            value={formData.maxMembers}
                            onChange={(e) => setFormData({ ...formData, maxMembers: Number(e.target.value) })}
                            className="slider"
                        />
                    </div>

                    {/* 공개 설정 */}
                    <div className="form-group">
                        <label>공개 설정</label>
                        <div className="mode-selection">
                            {PRIVACY_OPTIONS.map(option => (
                                <button
                                    key={String(option.value)}
                                    type="button"
                                    className={`mode-btn ${formData.isPrivate === option.value ? 'selected' : ''}`}
                                    onClick={() => setFormData({ ...formData, isPrivate: option.value })}
                                >
                                    <div className="mode-title">{option.title}</div>
                                    <div className="mode-desc">{option.description}</div>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* 스터디 모드 */}
                    <div className="form-group">
                        <label>스터디 모드</label>
                        <div className="mode-selection">
                            {STUDY_MODES.map(mode => (
                                <button
                                    key={mode.value}
                                    type="button"
                                    className={`mode-btn ${formData.studyMode === mode.value ? 'selected' : ''}`}
                                    onClick={() => setFormData({ ...formData, studyMode: mode.value })}
                                >
                                    <div className="mode-title">{mode.title}</div>
                                    <div className="mode-desc">{mode.description}</div>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Buttons */}
                    <div className="modal-footer">
                        <button type="button" className="cancel-btn" onClick={handleClose}>
                            취소
                        </button>
                        <button type="submit" className="submit-btn">
                            그룹 만들기
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}