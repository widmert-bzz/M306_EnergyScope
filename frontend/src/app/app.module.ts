import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { NgChartsModule } from 'ng2-charts';
import { AppComponent } from './app.component';
import { XmlUploaderComponent } from './xml-uploader/xml-uploader.component';
import { EnergyChartComponent } from './energy-chart/energy-chart.component';

@NgModule({
  declarations: [
    AppComponent,
    XmlUploaderComponent,
    EnergyChartComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    CommonModule,
    NgChartsModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
