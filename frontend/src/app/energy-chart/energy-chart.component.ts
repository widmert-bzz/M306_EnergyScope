import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Chart, registerables } from 'chart.js';
import { ChartConfiguration, ChartType } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { Subscription } from 'rxjs';
import { DataRefreshService } from '../shared/data-refresh.service';
import zoomPlugin from 'chartjs-plugin-zoom';

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
export class EnergyChartComponent implements OnInit, OnDestroy {
  @ViewChild(BaseChartDirective) chart: BaseChartDirective | undefined;

  private subscriptions: Subscription[] = [];

  sensorData: SensorData[] = [];
  measurementsByType: MeasurementsByType = {};
  selectedSensor: string | null = null;
  availableSensors: string[] = [];
  availableTimestamps: string[] = [];
  selectedTimestampRange: {
    start: string,
    end: string,
    startDate: string,
    endDate: string,
    startDateDisplay: string,
    endDateDisplay: string,
    startHour: any,
    endHour: any
  } = {
    start: '',
    end: '',
    startDate: '',
    endDate: '',
    startDateDisplay: '',
    endDateDisplay: '',
    startHour: '0',
    endHour: '23'
  };

  selectedDataTypes: SelectedDataTypes = {
    production: true,
    consumption: true,
    net: true
  };

  // Property to control hour input visibility
  hourInputEnabled: boolean = false;

  // Property to check if data is available for export
  get hasDataToExport(): boolean {
    return this.lineChartData.datasets.length > 0 &&
           Array.isArray(this.lineChartData.labels) &&
           this.lineChartData.labels.length > 0;
  }

  // Chart configuration
  public lineChartData: ChartConfiguration['data'] = {
    datasets: [],
    labels: []
  };

  public lineChartOptions: any = {
    responsive: true,
    elements: {
      line: {
        spanGaps: true
      }
    },
    maintainAspectRatio: false,
    scales: {
      x: {
        title: {
          display: true,
          text: 'Zeitstempel'
        },
        ticks: {
          maxRotation: 45,
          minRotation: 45
        }
      },
      y: {
        title: {
          display: true,
          text: 'Wert'
        },
        ticks: {
          callback: function(value: number, index: number, values: any[]) {
            // Format value with appropriate unit
            if (Math.abs(value) >= 1000000) {
              return (value / 1000000).toFixed(1) + ' MW';
            } else if (Math.abs(value) >= 1000) {
              return (value / 1000).toFixed(1) + ' kW';
            } else {
              return value + ' W';
            }
          }
        }
      }
    },
    plugins: {
      legend: {
        display: true
      },
      tooltip: {
        enabled: true,
        callbacks: {
          label: function(context: any) {
            let label = context.dataset.label || '';
            if (label) {
              label += ': ';
            }
            let value = context.parsed.y as number;
            if (value !== null) {
              if (Math.abs(value) >= 1000000) {
                label += (value / 1000000).toFixed(2) + ' MW';
              } else if (Math.abs(value) >= 1000) {
                label += (value / 1000).toFixed(2) + ' kW';
              } else {
                label += value.toFixed(2) + ' W';
              }
            }
            return label;
          }
        }
      },
      zoom: {
        pan: {
          enabled: true,
          mode: 'xy'
        },
        zoom: {
          wheel: {
            enabled: true,
            modifierKey: 'ctrl'
          },
          pinch: {
            enabled: true
          },
          mode: 'xy'
        }
      }
    },
    interaction: {
      mode: 'nearest',
      intersect: false
    },
    events: ['mousemove', 'mouseout', 'click', 'touchstart', 'touchmove']
  };

  public lineChartType: ChartType = 'line';

  constructor(
    private http: HttpClient,
    private dataRefreshService: DataRefreshService
  ) {}

  ngOnInit(): void {
    // Register Chart.js components and plugins
    Chart.register(...registerables, zoomPlugin);

    this.loadData();

    // Subscribe to data refresh events
    this.subscriptions.push(
      this.dataRefreshService.dataRefresh$.subscribe(() => {
        console.log('Data refresh event received, reloading data...');
        this.loadData();
      })
    );
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
          // Store the data but don't update chart immediately
          this.measurementsByType = data;

          // Only update the chart after all data is loaded
          setTimeout(() => {
            this.updateChart();
          }, 0);
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

        // Extract hours from the timestamps
        const startDate = new Date(this.selectedTimestampRange.start);
        const endDate = new Date(this.selectedTimestampRange.end);

        // Set hours
        this.selectedTimestampRange.startHour = startDate.getHours().toString();
        this.selectedTimestampRange.endHour = endDate.getHours().toString();

        // Convert ISO strings to date format (YYYY-MM-DD)
        this.selectedTimestampRange.startDate = this.formatDateForInput(startDate);
        this.selectedTimestampRange.endDate = this.formatDateForInput(endDate);

        // Set date display values
        this.selectedTimestampRange.startDateDisplay = this.formatDateForDisplay(startDate);
        this.selectedTimestampRange.endDateDisplay = this.formatDateForDisplay(endDate);

        // Reset hour input enabled state to false when changing sensors
        this.hourInputEnabled = false;
      }
    }
  }

  // Helper method to format Date objects for display
  private formatDateForDisplay(date: Date): string {
    // Format: DD/MM/YYYY
    return this.padZero(date.getDate()) + '/' +
           this.padZero(date.getMonth() + 1) + '/' +
           date.getFullYear();
  }


  // Handle date changes
  onStartDateChange(): void {
    if (this.selectedTimestampRange.startDate) {
      const date = new Date(this.selectedTimestampRange.startDate);
      this.selectedTimestampRange.startDateDisplay = this.formatDateForDisplay(date);
      this.onTimestampRangeChange();
    }
  }

  onEndDateChange(): void {
    if (this.selectedTimestampRange.endDate) {
      const date = new Date(this.selectedTimestampRange.endDate);
      this.selectedTimestampRange.endDateDisplay = this.formatDateForDisplay(date);
      this.onTimestampRangeChange();
    }
  }

  // Helper method to format Date objects for date input
  private formatDateForInput(date: Date): string {
    // Format: YYYY-MM-DD
    return date.getFullYear() + '-' +
           this.padZero(date.getMonth() + 1) + '-' +
           this.padZero(date.getDate());
  }

  // Helper method to pad single digits with leading zero
  private padZero(num: number): string {
    return num < 10 ? '0' + num : num.toString();
  }

  onSensorChange(): void {
    this.updateAvailableTimestamps();
    this.loadMeasurementsByType();
  }

  onTimestampRangeChange(): void {
    // Combine date and hour values to create full timestamps
    if (this.selectedTimestampRange.startDate) {
      // Create a date object from the date string
      const startDate = new Date(this.selectedTimestampRange.startDate);

      // Parse hour value from the text input
      const startHour = parseInt(this.selectedTimestampRange.startHour, 10);

      // Validate hour value (0-23)
      const validStartHour = isNaN(startHour) ? 0 : Math.max(0, Math.min(23, startHour));

      // Update the hour value if it was invalid
      if (validStartHour.toString() !== this.selectedTimestampRange.startHour) {
        this.selectedTimestampRange.startHour = validStartHour.toString();
      }

      // Set the hour from the hour input, and reset minutes and seconds to 0
      startDate.setHours(validStartHour, 0, 0);

      // Convert to ISO string
      this.selectedTimestampRange.start = startDate.toISOString();
    }

    if (this.selectedTimestampRange.endDate) {
      // Create a date object from the date string
      const endDate = new Date(this.selectedTimestampRange.endDate);

      // Parse hour value from the text input
      const endHour = parseInt(this.selectedTimestampRange.endHour, 10);

      // Validate hour value (0-23)
      const validEndHour = isNaN(endHour) ? 23 : Math.max(0, Math.min(23, endHour));

      // Update the hour value if it was invalid
      if (validEndHour.toString() !== this.selectedTimestampRange.endHour) {
        this.selectedTimestampRange.endHour = validEndHour.toString();
      }

      // Set the hour from the hour input, and set minutes and seconds to 59:59 to include the full hour
      endDate.setHours(validEndHour, 59, 59);

      // Convert to ISO string
      this.selectedTimestampRange.end = endDate.toISOString();
    }

    // Delay chart update to ensure all data is processed
    setTimeout(() => {
      this.updateChart();
    }, 0);
  }

  onDataTypeChange(): void {
    // Delay chart update to ensure all data is processed
    setTimeout(() => {
      this.updateChart();
    }, 0);
  }

  ngOnDestroy(): void {
    // Unsubscribe from all subscriptions to prevent memory leaks
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  resetZoom(): void {
    if (this.chart && this.chart.chart) {
      (this.chart.chart as any).resetZoom();
    }
  }

  // Export data as CSV
  exportAsCSV(): void {
    if (!this.hasDataToExport) return;

    // Create CSV content
    const headers = ['Zeitstempel'];
    const datasets = this.lineChartData.datasets;
    const labels = this.lineChartData.labels as string[];

    // Add dataset names to headers
    datasets.forEach(dataset => {
      headers.push(dataset.label || 'Unknown');
    });

    // Create CSV rows
    const rows: string[][] = [];

    // For each timestamp (label)
    for (let i = 0; i < labels.length; i++) {
      const row: string[] = [labels[i]];

      // Add value from each dataset for this timestamp
      for (let j = 0; j < datasets.length; j++) {
        const value = datasets[j].data[i];
        row.push(value !== null && value !== undefined ? value.toString() : '');
      }

      rows.push(row);
    }

    // Convert to CSV string
    let csvContent = headers.join(',') + '\n';
    rows.forEach(row => {
      csvContent += row.join(',') + '\n';
    });

    // Create filename with sensor ID and date
    const filename = this.generateFilename('csv');

    // Create and download the file
    this.downloadFile(csvContent, filename, 'text/csv');
  }

  // Export data as JSON
  exportAsJSON(): void {
    if (!this.hasDataToExport) return;

    const datasets = this.lineChartData.datasets;
    const labels = this.lineChartData.labels as string[];

    // Create JSON structure
    const jsonData: any = {
      sensor: this.selectedSensor,
      exportDatum: new Date().toISOString(),
      zeitbereich: {
        start: this.selectedTimestampRange.start,
        ende: this.selectedTimestampRange.end
      },
      daten: []
    };

    // For each timestamp (label)
    for (let i = 0; i < labels.length; i++) {
      const dataPoint: any = {
        zeitstempel: labels[i]
      };

      // Add value from each dataset for this timestamp
      for (let j = 0; j < datasets.length; j++) {
        const datasetLabel = datasets[j].label || 'unbekannt';
        dataPoint[datasetLabel] = datasets[j].data[i];
      }

      jsonData.daten.push(dataPoint);
    }

    // Convert to JSON string
    const jsonContent = JSON.stringify(jsonData, null, 2);

    // Create filename with sensor ID and date
    const filename = this.generateFilename('json');

    // Create and download the file
    this.downloadFile(jsonContent, filename, 'application/json');
  }

  // Helper method to generate filename
  private generateFilename(extension: string): string {
    const sensorPart = this.selectedSensor ? this.selectedSensor.replace(/[^a-zA-Z0-9]/g, '_') : 'unbekannt';
    const datePart = new Date().toISOString().split('T')[0];
    return `energiedaten_${sensorPart}_${datePart}.${extension}`;
  }

  // Helper method to download file
  private downloadFile(content: string, filename: string, contentType: string): void {
    const blob = new Blob([content], { type: contentType });
    const url = window.URL.createObjectURL(blob);

    // Create a link element and trigger download
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();

    // Clean up
    window.URL.revokeObjectURL(url);
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
        label: 'Produktion',
        backgroundColor: 'rgba(40, 167, 69, 0.2)',
        borderColor: 'rgba(40, 167, 69, 1)',
        pointRadius: 0,
        pointHoverBorderColor: 'rgba(40, 167, 69, 1)',
        fill: 'origin',
        spanGaps: true,
      });
    }

    // Consumption dataset (red)
    if (this.selectedDataTypes.consumption && this.measurementsByType.consumption) {
      const consumptionMap = new Map<string, number>();
      this.measurementsByType.consumption.forEach(d => consumptionMap.set(d.ts, d.value));

      datasets.push({
        data: filteredTimestamps.map(ts => {
          const value = consumptionMap.get(ts);
          return value !== undefined ? -value : null;
        }),
        label: 'Verbrauch',
        backgroundColor: 'rgba(220, 53, 69, 0.2)',
        borderColor: 'rgba(220, 53, 69, 1)',
        pointRadius: 0,
        fill: 'origin',
        spanGaps: true,
      });
    }

    // Net dataset (blue)
    if (this.selectedDataTypes.net && this.measurementsByType.net) {
      const netMap = new Map<string, number>();
      this.measurementsByType.net.forEach(d => netMap.set(d.ts, d.value));

      datasets.push({
        data: filteredTimestamps.map(ts => netMap.get(ts) || null),
        label: 'Netto',
        backgroundColor: 'rgba(0, 123, 255, 0.2)',
        borderColor: 'rgba(0, 123, 255, 1)',
        pointRadius: 0,
        fill: 'origin',
        spanGaps: true,
      });
    }

    // Update chart data
    this.lineChartData = {
      datasets: datasets,
      labels: filteredTimestamps.map(ts => {
        const date = new Date(ts);
        return date.toLocaleString('de-DE');
      })
    };

    // Update the chart if it exists
    if (this.chart) {
      this.chart.update();
    }
  }
}
