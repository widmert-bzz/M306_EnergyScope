import { Component, OnInit, ViewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Chart } from 'chart.js';
import { ChartConfiguration, ChartType } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

interface DataPoint {
  ts: string;
  value: number;
}

interface SensorData {
  sensorId: string;
  data: DataPoint[];
}

interface MeasurementsByType {
  production?: DataPoint[];
  consumption?: DataPoint[];
  net?: DataPoint[];
}

interface SelectedDataTypes {
  production: boolean;
  consumption: boolean;
  net: boolean;
}

@Component({
  selector: 'app-energy-chart',
  templateUrl: './energy-chart.component.html',
  styleUrls: ['./energy-chart.component.css']
})
export class EnergyChartComponent implements OnInit {
  @ViewChild(BaseChartDirective) chart: BaseChartDirective | undefined;

  sensorData: SensorData[] = [];
  measurementsByType: MeasurementsByType = {};
  selectedSensor: string | null = null;
  availableSensors: string[] = [];
  availableTimestamps: string[] = [];
  selectedTimestampRange: { start: string, end: string } = { start: '', end: '' };
  selectedDataTypes: SelectedDataTypes = {
    production: true,
    consumption: true,
    net: true
  };

  // Chart configuration
  public lineChartData: ChartConfiguration['data'] = {
    datasets: [],
    labels: []
  };

  public lineChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        title: {
          display: true,
          text: 'Timestamp'
        },
        ticks: {
          maxRotation: 45,
          minRotation: 45
        }
      },
      y: {
        title: {
          display: true,
          text: 'Value'
        }
      }
    },
    plugins: {
      legend: {
        display: true
      },
      tooltip: {
        enabled: true
      }
    },
    interaction: {
      mode: 'nearest',
      intersect: false
    },
    events: ['mousemove', 'mouseout', 'click', 'touchstart', 'touchmove']
  };

  public lineChartType: ChartType = 'line';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    // First load the list of sensors
    this.http.get<SensorData[]>('http://localhost:8080/export/json')
      .subscribe({
        next: (data) => {
          this.sensorData = data;
          this.availableSensors = data.map(sensor => sensor.sensorId);

          if (this.availableSensors.length > 0) {
            this.selectedSensor = this.availableSensors[0];
            this.updateAvailableTimestamps();
            this.loadMeasurementsByType();
          }
        },
        error: (error) => {
          console.error('Error loading data:', error);
        }
      });
  }

  loadMeasurementsByType(): void {
    if (!this.selectedSensor) return;

    // Load measurements grouped by type for the selected sensor
    this.http.get<MeasurementsByType>(`http://localhost:8080/export/json/measurements?meterId=${this.selectedSensor}`)
      .subscribe({
        next: (data) => {
          this.measurementsByType = data;
          this.updateChart();
        },
        error: (error) => {
          console.error('Error loading measurements by type:', error);
        }
      });
  }

  updateAvailableTimestamps(): void {
    if (!this.selectedSensor) return;

    const sensorData = this.sensorData.find(s => s.sensorId === this.selectedSensor);
    if (sensorData) {
      this.availableTimestamps = sensorData.data.map(d => d.ts).sort();

      if (this.availableTimestamps.length > 0) {
        this.selectedTimestampRange.start = this.availableTimestamps[0];
        this.selectedTimestampRange.end = this.availableTimestamps[this.availableTimestamps.length - 1];
      }
    }
  }

  onSensorChange(): void {
    this.updateAvailableTimestamps();
    this.loadMeasurementsByType();
  }

  onTimestampRangeChange(): void {
    this.updateChart();
  }

  updateChart(): void {
    if (!this.selectedSensor || !this.measurementsByType) return;

    // Get all timestamps from all data types
    const allTimestamps = new Set<string>();

    if (this.measurementsByType.production) {
      this.measurementsByType.production.forEach(d => allTimestamps.add(d.ts));
    }

    if (this.measurementsByType.consumption) {
      this.measurementsByType.consumption.forEach(d => allTimestamps.add(d.ts));
    }

    if (this.measurementsByType.net) {
      this.measurementsByType.net.forEach(d => allTimestamps.add(d.ts));
    }

    // Convert to array and sort
    const sortedTimestamps = Array.from(allTimestamps).sort((a, b) =>
      new Date(a).getTime() - new Date(b).getTime()
    );

    // Filter by timestamp range
    const startDate = new Date(this.selectedTimestampRange.start);
    const endDate = new Date(this.selectedTimestampRange.end);

    const filteredTimestamps = sortedTimestamps.filter(ts => {
      const date = new Date(ts);
      return date >= startDate && date <= endDate;
    });

    // Create datasets for each selected data type
    const datasets = [];

    // Production dataset (green)
    if (this.selectedDataTypes.production && this.measurementsByType.production) {
      const productionMap = new Map<string, number>();
      this.measurementsByType.production.forEach(d => productionMap.set(d.ts, d.value));

      datasets.push({
        data: filteredTimestamps.map(ts => productionMap.get(ts) || null),
        label: 'Production',
        backgroundColor: 'rgba(40, 167, 69, 0.2)',
        borderColor: 'rgba(40, 167, 69, 1)',
        pointBackgroundColor: 'rgba(40, 167, 69, 1)',
        pointBorderColor: '#fff',
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: 'rgba(40, 167, 69, 1)',
        fill: 'origin',
      });
    }

    // Consumption dataset (red)
    if (this.selectedDataTypes.consumption && this.measurementsByType.consumption) {
      const consumptionMap = new Map<string, number>();
      this.measurementsByType.consumption.forEach(d => consumptionMap.set(d.ts, d.value));

      datasets.push({
        data: filteredTimestamps.map(ts => consumptionMap.get(ts) || null),
        label: 'Consumption',
        backgroundColor: 'rgba(220, 53, 69, 0.2)',
        borderColor: 'rgba(220, 53, 69, 1)',
        pointBackgroundColor: 'rgba(220, 53, 69, 1)',
        pointBorderColor: '#fff',
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: 'rgba(220, 53, 69, 1)',
        fill: 'origin',
      });
    }

    // Net dataset (blue)
    if (this.selectedDataTypes.net && this.measurementsByType.net) {
      const netMap = new Map<string, number>();
      this.measurementsByType.net.forEach(d => netMap.set(d.ts, d.value));

      datasets.push({
        data: filteredTimestamps.map(ts => netMap.get(ts) || null),
        label: 'Net',
        backgroundColor: 'rgba(0, 123, 255, 0.2)',
        borderColor: 'rgba(0, 123, 255, 1)',
        pointBackgroundColor: 'rgba(0, 123, 255, 1)',
        pointBorderColor: '#fff',
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: 'rgba(0, 123, 255, 1)',
        fill: 'origin',
      });
    }

    // Update chart data
    this.lineChartData = {
      datasets: datasets,
      labels: filteredTimestamps.map(ts => {
        const date = new Date(ts);
        return date.toLocaleString();
      })
    };

    // Update the chart if it exists
    if (this.chart) {
      this.chart.update();
    }
  }
}
