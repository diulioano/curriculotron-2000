import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type FormEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Select, type SelectOption } from '../../src/components/Select';

type Fruit = 'apple' | 'banana' | 'cherry';
const OPTIONS: readonly SelectOption<Fruit>[] = [
  { value: 'apple', label: 'Maçã' },
  { value: 'banana', label: 'Banana' },
  { value: 'cherry', label: 'Cereja' },
];

function Harness({ onChange, disabled }: { onChange?: (v: Fruit) => void; disabled?: boolean }) {
  const [value, setValue] = useState<Fruit>('apple');
  return (
    <>
      <label htmlFor="fruit">Fruta</label>
      <Select
        id="fruit"
        value={value}
        options={OPTIONS}
        disabled={disabled}
        onChange={(next) => {
          setValue(next);
          onChange?.(next);
        }}
      />
      <button type="button">fora</button>
    </>
  );
}

const trigger = () => screen.getByRole('combobox', { name: 'Fruta' });

describe('Select', () => {
  it('shouldExposeLabelAndCurrentValueWhileClosed', () => {
    render(<Harness />);
    expect(trigger()).toHaveTextContent('Maçã');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('shouldSelectOptionOnClick', async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await userEvent.click(trigger());
    expect(screen.getByRole('listbox')).toBeVisible();
    expect(screen.getByRole('option', { name: 'Maçã' })).toHaveAttribute('aria-selected', 'true');
    await userEvent.click(screen.getByRole('option', { name: 'Cereja' }));
    expect(onChange).toHaveBeenCalledWith('cherry');
    expect(trigger()).toHaveTextContent('Cereja');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });

  it('shouldNavigateAndSelectWithKeyboard', async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    trigger().focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(trigger()).toHaveAttribute('aria-expanded', 'true');
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowDown}');
    const active = trigger().getAttribute('aria-activedescendant');
    expect(document.getElementById(active ?? '')).toHaveTextContent('Cereja');
    await userEvent.keyboard('{Enter}');
    expect(onChange).toHaveBeenCalledWith('cherry');
    expect(trigger()).toHaveFocus();
  });

  it('shouldCloseWithoutChangingOnEscape', async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    trigger().focus();
    await userEvent.keyboard('{Enter}{End}{Escape}');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
    expect(onChange).not.toHaveBeenCalled();
    expect(trigger()).toHaveTextContent('Maçã');
  });

  it('shouldJumpToOptionByTypeahead', async () => {
    render(<Harness />);
    trigger().focus();
    await userEvent.keyboard('c');
    expect(trigger()).toHaveAttribute('aria-expanded', 'true');
    await userEvent.keyboard('{Enter}');
    expect(trigger()).toHaveTextContent('Cereja');
  });

  it('shouldCloseWhenClickingOutside', async () => {
    render(<Harness />);
    await userEvent.click(trigger());
    await userEvent.click(screen.getByRole('button', { name: 'fora' }));
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });

  it('shouldCommitActiveOptionOnTab', async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    trigger().focus();
    await userEvent.keyboard('{ArrowDown}{End}{ArrowUp}');
    await userEvent.tab();
    expect(onChange).toHaveBeenCalledWith('banana');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });

  it('shouldShowFirstOptionWhenValueIsUnknown', () => {
    render(
      <>
        <label htmlFor="fruit">Fruta</label>
        <Select id="fruit" value={'grape' as Fruit} options={OPTIONS} onChange={vi.fn()} />
      </>,
    );
    expect(trigger()).toHaveTextContent('Maçã');
  });

  it('shouldNotSubmitEnclosingFormWhenSelectingWithKeyboard', async () => {
    const onSubmit = vi.fn((event: FormEvent) => event.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <Harness />
      </form>,
    );
    trigger().focus();
    await userEvent.keyboard('{Enter}{ArrowDown}{Enter}{ }{Escape}');
    expect(onSubmit).not.toHaveBeenCalled();
    expect(trigger()).toHaveTextContent('Banana');
  });

  it('shouldNotOpenWhenOptionsAreEmpty', async () => {
    render(
      <>
        <label htmlFor="fruit">Fruta</label>
        <Select id="fruit" value={'apple' as Fruit} options={[]} onChange={vi.fn()} />
      </>,
    );
    await userEvent.click(trigger());
    trigger().focus();
    await userEvent.keyboard('{End}');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });

  it('shouldNotOpenWhenDisabled', async () => {
    render(<Harness disabled />);
    expect(trigger()).toBeDisabled();
    await userEvent.click(trigger());
    expect(screen.queryByRole('listbox')).toBeNull();
  });
});
