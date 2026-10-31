/**
 * ECharts 按需注册 + D2 暖调中性紫色板
 *
 * 只注册看板用到的图表与组件，避免把整个 echarts（约 1MB）打进包里。
 */
import * as echarts from 'echarts/core'
import { BarChart, HeatmapChart, LineChart, PieChart } from 'echarts/charts'
import {
  CalendarComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  HeatmapChart,
  LineChart,
  PieChart,
  CalendarComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  VisualMapComponent,
  CanvasRenderer
])

/** D2 暖调中性紫 */
export const d2 = {
  primary: '#3F4A5A',
  primaryLight: '#5D6878',
  violet: '#7A6A93',
  camel: '#B99A7A',
  text: '#282624',
  muted: 'rgba(40,38,36,.45)',
  grid: 'rgba(63,74,90,.06)',
  axis: 'rgba(63,74,90,.14)',
  track: 'rgba(63,74,90,.08)',
  tooltip: 'rgba(40,38,36,.92)'
}

/** 自上而下的线性渐变（柱状图、面积填充） */
export function verticalGradient(from, to) {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: from },
    { offset: 1, color: to }
  ])
}

/** 斜向渐变（环图进度） */
export function diagonalGradient(from, to) {
  return new echarts.graphic.LinearGradient(0, 0, 1, 1, [
    { offset: 0, color: from },
    { offset: 1, color: to }
  ])
}

/** 统一的 tooltip 样式 */
export const tooltipStyle = {
  backgroundColor: d2.tooltip,
  borderWidth: 0,
  padding: [8, 12],
  textStyle: { color: '#fff', fontSize: 12 },
  extraCssText: 'border-radius:10px;box-shadow:0 12px 28px -18px rgba(40,38,36,.9);'
}

export default echarts
