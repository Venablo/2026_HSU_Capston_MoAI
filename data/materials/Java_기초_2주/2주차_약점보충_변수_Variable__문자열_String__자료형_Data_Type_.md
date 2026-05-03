# 2주차 약점 보충 자료

## 약점 보충 학습

### 🔁 약점 보충 학습 목표
이번 2주차 파이널 퀴즈에서 학습자님께서는 기초 데이터 처리와 객체 제어의 핵심인 변수와 리스트 구조에서 혼동을 겪으셨습니다. 특히 Java의 엄격한 자료형(Data Type) 규칙과 String 객체의 불변성(Immutability), 그리고 리스트(List)의 인덱싱 및 슬라이싱 기법은 실무에서 데이터를 다룰 때 가장 빈번하게 사용되는 기본기입니다. 이번 보충 학습을 통해 변수의 메모리 할당 원리부터, String 클래스의 다양한 메서드 활용, 그리고 List 인터페이스의 동적 배열 특성을 완전히 이해하여 코드 작성의 정확성을 높이는 것을 목표로 합니다.

### 키워드별 심화 정리

#### 1. 변수(Variable)와 자료형(Data Type)
변수는 데이터를 저장하기 위한 메모리 공간의 이름입니다. Java는 정적 타입(Static Typing) 언어로, 변수 선언 시 자료형을 명시해야 합니다. 기본 자료형(Primitive Type: int, double, boolean 등)은 값을 직접 저장하며, 참조 자료형(Reference Type: String, List 등)은 객체의 메모리 주소를 저장합니다. 
- **원리**: 기본형은 스택(Stack) 메모리에 실제 값이 저장되고, 참조형은 스택에 주소값을, 실제 데이터 객체는 힙(Heap) 메모리에 저장됩니다.
- **예시**: `int age = 25;` (기본형), `String name = "MoAI";` (참조형).
- **자주 하는 실수**: 객체 비교 시 `==` 연산자를 사용하여 주소값을 비교하는 오류를 자주 범합니다. 객체 내부의 값 비교는 반드시 `.equals()` 메서드를 사용해야 합니다.

#### 2. 문자열(String)과 이스케이프 코드(Escape Code)
String은 Java에서 가장 많이 사용되는 클래스로, 불변(Immutable) 객체입니다. 한번 생성된 문자열은 변경할 수 없으며, 수정 시 새로운 객체가 생성됩니다.
- **원리**: 문자열 내부의 특수 문자를 표현하기 위해 이스케이프 코드(`\n`, `\t`, `\"` 등)를 사용합니다.
- **예시**: `String str = "Hello\nWorld";`는 줄바꿈을 포함합니다.
- **자주 하는 실수**: 문자열을 반복문 내에서 `+=`로 계속 더할 경우, 불필요한 객체가 힙 메모리에 무수히 생성되어 성능 저하를 일으킵니다. 이때는 `StringBuilder`를 사용하는 것이 올바른 패턴입니다.

#### 3. 인덱싱(Indexing)과 슬라이싱(Slicing)
인덱싱은 특정 위치의 요소에 접근하는 것이며, 슬라이싱은 데이터의 범위를 잘라내는 작업입니다. Java의 List와 String은 0부터 시작하는 인덱스를 가집니다.
- **원리**: String의 경우 `.substring(start, end)` 메서드를 통해 슬라이싱합니다. 이때 `start`는 포함하고 `end`는 포함하지 않는(exclusive) 범위라는 점을 유의해야 합니다.
- **예시**: `"MoAI".substring(0, 2)`는 "Mo"를 반환합니다.
- **자주 하는 실수**: 배열이나 리스트의 길이를 넘어선 인덱스에 접근하여 `ArrayIndexOutOfBoundsException` 예외를 발생시키는 경우가 많습니다. 반드시 `.length()`나 `.size()`로 경계를 확인해야 합니다.

#### 4. 리스트(List)와 리스트 함수(List Function)
Java의 List는 가변(Mutable) 배열 구조인 `ArrayList`가 대표적입니다. 크기가 동적으로 변하며 데이터 추가, 삭제가 자유롭습니다.
- **원리**: `ArrayList<String> list = new ArrayList<>();`와 같이 선언합니다. `.add()`, `.remove()`, `.get()`, `.size()`가 핵심 함수입니다.
- **예시**: `list.add("Java");`, `list.remove(0);`
- **자주 하는 실수**: `List` 타입 자체는 인터페이스이므로 객체를 생성할 때는 반드시 구현체인 `ArrayList`나 `LinkedList`를 사용해야 합니다. 또한, 리스트에 기본 자료형을 넣을 때는 Wrapper 클래스(`Integer`, `Double`)가 자동 변환(Autoboxing)되는 원리를 이해해야 합니다.

#### 5. 포맷팅(Formatting)
문자열 내에 데이터를 변수 형태로 삽입하는 방법입니다. `String.format()` 혹은 `System.out.printf()`를 주로 사용합니다.
- **원리**: `%d`(정수), `%s`(문자열), `%f`(실수) 등의 서식 지정자를 사용하여 가독성을 높입니다.
- **예시**: `String.format("이름: %s, 나이: %d", name, age);`
- **자주 하는 실수**: 지정자의 자료형과 실제 들어갈 변수의 자료형을 일치시키지 않아 `IllegalFormatException`이 발생하는 경우가 많습니다.

### 비교 정리표

| 구분 | String | List (ArrayList) | 인덱싱/슬라이싱 방식 |
| :--- | :--- | :--- | :--- |
| **변경 가능성** | 불변 (Immutable) | 가변 (Mutable) | 읽기 전용 / 부분 추출 |
| **메모리** | Heap (String Pool) | Heap | N/A |
| **주요 연산** | concat, substring | add, remove, get | .charAt(), .substring() |
| **크기** | 고정됨 | 동적 조절 가능 | 인덱스 범위 확인 필수 |

### 핵심 암기 포인트 & 체크리스트
- [ ] **String은 불변(Immutable)이다**: 값이 바뀔 때마다 새로운 메모리가 할당됨을 인지했는가?
- [ ] **인덱스는 0부터 시작**: 마지막 인덱스는 `길이-1`임을 항상 체크하는가?
- [ ] **`.equals()` 사용**: 문자열이나 객체 비교 시 `==` 대신 `.equals()`를 사용하고 있는가?
- [ ] **슬라이싱 범위**: `substring(start, end)`에서 `end`는 포함되지 않음을 기억하는가?
- [ ] **리스트 선언**: `List<Type> list = new ArrayList<Type>();` 패턴을 숙지했는가?
- [ ] **예외 처리**: `IndexOutOfBoundsException` 발생 가능성을 고려하여 `size()` 체크를 수행하는가?
