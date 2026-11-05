import type * as React from 'react';
export type IconName = 'battery' | 'bolt' | 'plug' | 'thermo' | 'route' | 'lock' | 'unlock' | 'car' | 'bluetooth' | 'pin' | 'refresh' | 'sliders' | 'help' | 'arrow-left' | 'arrow-right' | 'sun' | 'grid' | 'clock';
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> { variant?: 'primary' | 'tonal' | 'secondary' | 'ghost' | 'danger'; size?: 'md' | 'sm'; block?: boolean; icon?: IconName; iconAfter?: IconName }
export declare function Button(props: ButtonProps): React.ReactElement;
export interface LabelProps { tone?: 'neutral' | 'teal' | 'live' | 'signal' | 'warn' | 'outline'; dot?: boolean; children?: React.ReactNode }
export declare function Label(props: LabelProps): React.ReactElement;
export interface FieldProps extends React.InputHTMLAttributes<HTMLInputElement> { label: string; unit?: string; hint?: string }
export declare function Field(props: FieldProps): React.ReactElement;
export interface SwitchProps { label: string; checked?: boolean; defaultChecked?: boolean; disabled?: boolean; onChange?: (on: boolean) => void; id?: string }
export declare function Switch(props: SwitchProps): React.ReactElement;
export interface RadioOption { value: string; label: string; hint?: string }
export interface RadioProps { label: string; options: RadioOption[]; value?: string; defaultValue?: string; onChange?: (value: string) => void; name?: string }
export declare function Radio(props: RadioProps): React.ReactElement;
export interface SliderProps { label: string; min?: number; max?: number; step?: number; value?: number; defaultValue?: number; unit?: string; scale?: boolean; onChange?: (v: number) => void }
export declare function Slider(props: SliderProps): React.ReactElement;
export interface SettingRowProps { title: string; description?: string; control?: React.ReactNode }
export declare function SettingRow(props: SettingRowProps): React.ReactElement;
export interface ReadoutProps { label?: string; value: React.ReactNode; unit?: string; note?: React.ReactNode; size?: 'lg' | 'sm' }
export declare function Readout(props: ReadoutProps): React.ReactElement;
export interface SegmentMeterProps { value: number; segments?: number; tone?: 'teal' | 'signal'; label?: string; scale?: boolean }
export declare function SegmentMeter(props: SegmentMeterProps): React.ReactElement;
export interface StatTileProps { icon?: IconName; value: React.ReactNode; label: string }
export declare function StatTile(props: StatTileProps): React.ReactElement;
export interface CardProps { tone?: 'raised' | 'dark' | 'hero'; cut?: 'none' | 'sm' | 'md' | 'lg'; eyebrow?: string; title?: React.ReactNode; meta?: React.ReactNode; pixels?: string[]; pixelsAt?: 'top' | 'bottom'; children?: React.ReactNode; style?: React.CSSProperties; bodyStyle?: React.CSSProperties }
export declare function Card(props: CardProps): React.ReactElement;
export interface TopBarAction { icon: IconName; label: string; onClick?: () => void }
export interface TopBarProps { title?: string; brand?: React.ReactNode; back?: boolean; onBack?: () => void; actions?: TopBarAction[] }
export declare function TopBar(props: TopBarProps): React.ReactElement;
export interface PixelGridProps { pattern: string[]; cell?: number; gap?: number; style?: React.CSSProperties }
export declare function PixelGrid(props: PixelGridProps): React.ReactElement;
export interface MarkProps { size?: number; tone?: 'auto' | 'light'; compact?: boolean; word?: boolean; stacked?: boolean; wordSize?: number }
export declare function Mark(props: MarkProps): React.ReactElement;
export interface IconProps { name: IconName; size?: number; title?: string }
export declare function Icon(props: IconProps): React.ReactElement;
declare global { interface Window { Porcelain: { Button: typeof Button; Label: typeof Label; Field: typeof Field; Switch: typeof Switch; Radio: typeof Radio; Slider: typeof Slider; SettingRow: typeof SettingRow; Readout: typeof Readout; SegmentMeter: typeof SegmentMeter; StatTile: typeof StatTile; Card: typeof Card; Panel: typeof Card; TopBar: typeof TopBar; PixelGrid: typeof PixelGrid; Mark: typeof Mark; Icon: typeof Icon } } }
