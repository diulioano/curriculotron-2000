import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

export interface SelectOption<T extends string> {
  value: T;
  label: string;
}

interface SelectProps<T extends string> {
  /** Id do botão; usar no `htmlFor` do `<label>` externo. */
  id: string;
  value: T;
  options: readonly SelectOption<T>[];
  onChange: (value: T) => void;
  disabled?: boolean;
}

/**
 * Seletor estilizado no padrão "select-only combobox" da WAI-ARIA APG:
 * o foco permanece no botão e a opção ativa é anunciada por `aria-activedescendant`.
 */
export function Select<T extends string>({
  id,
  value,
  options,
  onChange,
  disabled = false,
}: SelectProps<T>) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  );
  const [activeIndex, setActiveIndex] = useState(selectedIndex);
  const selected = options[selectedIndex];

  useEffect(() => {
    if (!open) return;
    const closeOnOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', closeOnOutside);
    return () => document.removeEventListener('mousedown', closeOnOutside);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    document.getElementById(optionId(activeIndex))?.scrollIntoView?.({ block: 'nearest' });
  });

  function optionId(index: number) {
    return `${listId}-opt-${index}`;
  }

  function openList(index = selectedIndex) {
    setActiveIndex(index);
    setOpen(true);
  }

  function choose(index: number) {
    const option = options[index];
    if (option && option.value !== value) onChange(option.value);
    setOpen(false);
  }

  function matchTypeahead(key: string) {
    const start = open ? activeIndex + 1 : selectedIndex + 1;
    const lower = key.toLocaleLowerCase('pt-BR');
    for (let step = 0; step < options.length; step++) {
      const index = (start + step) % options.length;
      if (options[index]?.label.toLocaleLowerCase('pt-BR').startsWith(lower)) return index;
    }
    return -1;
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (options.length === 0) return;
    const last = options.length - 1;
    const keyActions: Record<string, () => void> = open
      ? {
          ArrowDown: () => setActiveIndex((i) => Math.min(last, i + 1)),
          ArrowUp: () =>
            event.altKey ? choose(activeIndex) : setActiveIndex((i) => Math.max(0, i - 1)),
          Home: () => setActiveIndex(0),
          End: () => setActiveIndex(last),
          Enter: () => choose(activeIndex),
          ' ': () => choose(activeIndex),
          Escape: () => setOpen(false),
        }
      : {
          ArrowDown: () => openList(),
          ArrowUp: () => openList(),
          Enter: () => openList(),
          ' ': () => openList(),
          Home: () => openList(0),
          End: () => openList(last),
        };
    const action = keyActions[event.key];
    if (action) {
      event.preventDefault();
      action();
      return;
    }
    if (event.key === 'Tab' && open) {
      choose(activeIndex);
      return;
    }
    if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      const index = matchTypeahead(event.key);
      if (index >= 0) openList(index);
    }
  }

  return (
    <div className={open ? 'select open' : 'select'} ref={rootRef}>
      <button
        type="button"
        id={id}
        className="input select-trigger"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={open ? optionId(activeIndex) : undefined}
        disabled={disabled}
        onClick={() => (open ? setOpen(false) : options.length > 0 && openList())}
        onKeyDown={onKeyDown}
      >
        <span className="select-value">{selected?.label ?? ''}</span>
        <span className="select-chevron" aria-hidden="true" />
      </button>
      <ul className="select-list" id={listId} role="listbox" aria-labelledby={id} hidden={!open}>
        {options.map((option, index) => (
          <li
            key={option.value}
            id={optionId(index)}
            role="option"
            aria-selected={option.value === value}
            className={index === activeIndex ? 'select-option active' : 'select-option'}
            onMouseDown={(event) => event.preventDefault()}
            onMouseEnter={() => setActiveIndex(index)}
            onClick={() => choose(index)}
          >
            {option.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
