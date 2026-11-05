#!/usr/bin/env python3
"""Independent readers for synthetic native Android export fixtures; no accounts/network."""
import argparse
import csv
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from importlib.metadata import version
from pathlib import Path
from zipfile import ZipFile
from docx import Document
from openpyxl import load_workbook
from openpyxl.utils.escape import unescape
from pptx import Presentation
from PIL import Image
from pypdf import PdfReader

FA = 'گزارش فارسی — سلام دنیا & <آزمایش>'

def validate(folder, output, office):
    output.mkdir(parents=True, exist_ok=True)
    assert FA in '\n'.join(p.text for p in Document(folder / 'fixture.docx').paragraphs)
    sheet = load_workbook(folder / 'fixture.xlsx', data_only=False).active
    assert sheet['B2'].value == '=1+1' and sheet['B2'].data_type == 's'
    assert sheet['B4'].value == '00123' and sheet['B4'].data_type == 's'
    deck = Presentation(folder / 'fixture.pptx')
    assert len(deck.slides) == 2
    assert any(FA in shape.text for slide in deck.slides for shape in slide.shapes if shape.has_text_frame)
    assert len(PdfReader(folder / 'fixture.pdf').pages) >= 2
    with Image.open(folder / 'fixture.png') as image:
        image.load()
        assert image.format == 'PNG' and image.size == (800, 600)
    assert ET.parse(folder / 'fixture.svg').getroot().tag == '{http://www.w3.org/2000/svg}svg'
    with ZipFile(folder / 'fixture.zip') as archive:
        assert archive.testzip() is None
        assert archive.read('notes/فارسی.txt').decode() == FA
    assert (folder / 'fixture.txt').read_text() == FA
    assert FA in (folder / 'fixture.kt').read_text()
    expected = ['_x0041_', '_x000D_', '_x005F_', '_x0041_x0042_', '_x005F_x0041_', FA, '=1+1', '+SUM(A1)', '00123']
    literals = load_workbook(folder / 'spreadsheet-literals.xlsx').active
    assert [unescape(c.value) for c in next(literals.rows)] == expected
    boundary = load_workbook(folder / 'spreadsheet-boundary.xlsx').active
    assert all(len(boundary.cell(i, 1).value.encode('utf-16-le')) // 2 == 32767 for i in (1, 2))
    conversions = []
    if office:
        for extension in ('docx', 'xlsx', 'pptx'):
            destination = output / extension
            destination.mkdir()
            with tempfile.TemporaryDirectory(prefix='saba-lo-') as profile:
                subprocess.run(['libreoffice', '-env:UserInstallation=' + Path(profile).as_uri(), '--headless',
                                '--convert-to', 'pdf', '--outdir', str(destination), str(folder / ('fixture.' + extension))],
                               check=True, timeout=90, capture_output=True)
            pdf = destination / 'fixture.pdf'
            assert pdf.is_file() and len(PdfReader(pdf).pages) >= 1, extension
            conversions.append(extension + ' to actual readable PDF')
        csvdir = output / 'spreadsheet-csv'
        csvdir.mkdir()
        with tempfile.TemporaryDirectory(prefix='saba-lo-') as profile:
            subprocess.run(['libreoffice', '-env:UserInstallation=' + Path(profile).as_uri(), '--headless',
                            '--convert-to', 'csv:Text - txt - csv (StarCalc):44,34,76', '--outdir', str(csvdir),
                            str(folder / 'spreadsheet-literals.xlsx')], check=True, timeout=90, capture_output=True)
        with (csvdir / 'spreadsheet-literals.csv').open(encoding='utf-8-sig', newline='') as stream:
            assert next(csv.reader(stream)) == expected, 'LibreOffice did not preserve literal spreadsheet strings'
        conversions.append('SpreadsheetML literals preserved by LibreOffice CSV conversion')
    report = {'status': 'passed', 'fixture_origin': 'native Android instrumentation', 'physical_device': False,
              'readers': {name: version(name) for name in ('python-docx', 'openpyxl', 'python-pptx', 'Pillow', 'pypdf')},
              'libreoffice_conversions': conversions, 'limitations': ['No claim of testing Microsoft Office or all application editing flows.']}
    (output / 'reader-evidence.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('artifact_root', type=Path)
    parser.add_argument('--output', type=Path, default=Path('document-reader-evidence'))
    parser.add_argument('--libreoffice', action='store_true')
    arguments = parser.parse_args()
    candidates = list(arguments.artifact_root.rglob('fixture.docx'))
    assert len(candidates) == 1, 'Exactly one native fixture directory is required'
    validate(candidates[0].parent, arguments.output, arguments.libreoffice)
