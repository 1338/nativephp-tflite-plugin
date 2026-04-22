<?php

namespace OneThreeThreeEight\NativephpTflite\Events;

class WakewordDetected
{
    public $score;
    public $timestamp;

    public function __construct($score, $timestamp)
    {
        $this->score = (float)$score;
        $this->timestamp = (float)$timestamp;
    }
}
